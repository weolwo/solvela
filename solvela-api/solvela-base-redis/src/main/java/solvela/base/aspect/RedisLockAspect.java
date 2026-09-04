package solvela.base.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.annotation.RedisLock;
import solvela.base.util.SolvelaStringUtil;
import org.apache.logging.log4j.util.Strings;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * {@link RedisLock} 的实现：在方法外面包一把 Redisson 分布式锁。
 *
 * <h3>⚠️ 切点曾经写的是 {@code com.*.annotation.RedisLock}</h3>
 * 那是包名改成 {@code solvela.*} 之前留下的，<b>它匹配不到任何方法</b> ——
 * 也就是说这个切面一直没有生效过。当时没被发现是因为全工程还没有任何地方用
 * {@code @RedisLock}（动账链路自己在 {@code AbstractAssetHandler} 里手写了加锁）。
 * 但这种失效是<b>静默的</b>：下一个给方法加上注解的人不会收到任何提示，
 * 只会得到一个没有锁的临界区。切点已订正为注解的真实全限定名。
 *
 * <p>加锁必须<b>包在事务外面</b>（{@code @Order(HIGHEST_PRECEDENCE)}）：
 * 反过来的话锁在事务提交之前就释放了，下一个线程读到的还是旧数据，等于没锁。
 *
 * @author: blue
 * @date: 2024-06-09
 * @version: 1.0
 */

@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)//优先级必须最高，需要包裹住事务
@Component
@RequiredArgsConstructor
public class RedisLockAspect {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private final RedissonClient redissonClient;

    @Around("@annotation(solvela.base.annotation.RedisLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RedisLock annotation = method.getAnnotation(RedisLock.class);

        String dynamicKey = parseKey(joinPoint);
        String lockKey = SolvelaStringUtil.join(":", annotation.prefixKey(), dynamicKey);

        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;
        try {
            long waitTime = annotation.waitTime();
            long leaseTime = annotation.leaseTime();
            // 1. 尝试加锁 (支持自定义等待时间，完美兼容 Fail-fast 和 阻塞等待 两种场景)
            try {
                if (leaseTime == -1) {
                    // 激活看门狗
                    isLocked = lock.tryLock(waitTime, annotation.unit());
                } else {
                    // 自定义租期
                    isLocked = lock.tryLock(waitTime, leaseTime, annotation.unit());
                }
            } catch (InterruptedException e) {
                // 2. 响应中断，恢复线程状态，并抛出业务异常
                Thread.currentThread().interrupt();
                log.error("尝试获取分布式锁时线程被中断，Key: {}", lockKey, e);
                throw new RuntimeException("系统繁忙，请稍后再试");
            }

            if (!isLocked) {
                log.warn("获取分布式锁失败，阻止了并发操作。Key: {}", lockKey);
                throw new RuntimeException("操作太频繁，请稍后再试");
            }

            // 3. 执行核心业务
            return joinPoint.proceed();

        } finally {
            // 去掉了冗余的 lock.isLocked() 减少一次 Redis IO
            if (isLocked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.debug("释放分布式锁成功，Key: {}", lockKey);
                } catch (Exception e) {
                    //防止解锁时的网络抖动掩盖了正常业务结果或导致事务意外回滚
                    log.error("释放分布式锁出现异常，Key: {} (可能 Redis 连接异常或锁已过期自动释放)", lockKey, e);
                }
            }
        }
    }

    private String parseKey(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RedisLock annotation = method.getAnnotation(RedisLock.class);
        String key = annotation.key();
        if (SolvelaStringUtil.isEmpty(key)) {
            return Strings.EMPTY;
        }
        // el解析需要的上下文对象
        EvaluationContext context = new StandardEvaluationContext();
        // 参数名
        String[] params = parameterNameDiscoverer.getParameterNames(method);
        if (Objects.isNull(params)) {
            return key;
        }
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);//所有参数都作为原材料扔进去
        }
        Expression expression = parser.parseExpression(key);
        return expression.getValue(context, String.class);
    }


}
