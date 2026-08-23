package sa.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import sa.base.common.exception.BusinessException;
import sa.base.common.exception.EngineScriptException;
import sa.scriptengine.core.ScriptEngineProperties;
import sa.scriptengine.domain.ExecutableScript;
import sa.scriptengine.spi.EngineContext;

/**
 * 脚本执行的监控与异常兜底切面。
 *
 * <p>切点打在 {@code ScriptEngine} 接口上而不是某个实现类：门面是通过 {@code @Bean} 返回接口类型
 * 装配的，Spring 会走 JDK 动态代理，按实现类的包名写切点会一个方法都拦不到。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class EngineExecutionMonitorAspect {

    private final ScriptEngineProperties properties;

    @Pointcut("execution(* sa.scriptengine.spi.ScriptEngine+.evaluate(..))")
    public void evaluatePointcut() {
    }

    @Around("evaluatePointcut()")
    public Object monitorAndRescue(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object[] args = pjp.getArgs();
        ExecutableScript script = args.length > 0 && args[0] instanceof ExecutableScript s ? s : null;
        EngineContext context = args.length > 1 && args[1] instanceof EngineContext c ? c : null;
        String scriptName = script != null ? script.name() : "unknown";

        try {
            Object result = pjp.proceed();

            long costTime = System.currentTimeMillis() - startTime;
            if (costTime >= properties.getSlowLogMillis()) {
                log.warn("[ScriptEngine] 慢脚本 耗时: {}ms, 脚本: {}", costTime, scriptName);
            } else if (log.isDebugEnabled()) {
                log.debug("[ScriptEngine] 执行完成 耗时: {}ms, 脚本: {}", costTime, scriptName);
            }
            return result;

        } catch (EngineScriptException e) {
            // 脚本自身的问题：把 QL 给出的行列号一起抛给上层，前端编辑器可以直接划红线
            log.error("[ScriptEngine] 脚本执行失败 耗时: {}ms, 脚本: {}, 上下文: {}, 原因: {}",
                    System.currentTimeMillis() - startTime, scriptName, context, e.getScriptErrorDetail());
            throw new BusinessException(e.getMessage() + " 详细原因: " + e.getScriptErrorDetail());

        } catch (BusinessException e) {
            // 脚本里调用的 Java 函数抛出的业务异常，原样放行
            throw e;

        } catch (Throwable e) {
            log.error("[ScriptEngine] 未知异常 耗时: {}ms, 脚本: {}, 上下文: {}",
                    System.currentTimeMillis() - startTime, scriptName, context, e);
            throw new BusinessException("系统动态规则执行遇到未知异常，请联系管理员！");
        }
    }
}
