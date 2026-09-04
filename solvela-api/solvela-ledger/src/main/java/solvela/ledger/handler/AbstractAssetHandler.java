package solvela.ledger.handler;

import lombok.extern.slf4j.Slf4j;
import solvela.dispatch.DispatchOutcome;
import solvela.risk.ProposalRecord;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import jakarta.annotation.Resource;

import java.util.concurrent.TimeUnit;

/**
 * 所有资产下发策略的加锁骨架。<b>只管加锁与放行，不认识任何一种资产</b>。
 *
 * <h3>这一层为什么单独存在</h3>
 * 动账链路上有四层，各自只知道一件事，谁也不越界：
 * <ul>
 *   <li><b>本类</b> —— 拿锁、放行、无论如何都释放。锁的粒度由子类给，它不关心；</li>
 *   <li><b>各 handler（门面）</b> —— 把 Service 抛的领域异常翻译成引擎认识的
 *       {@link DispatchOutcome}；</li>
 *   <li><b>各 Service</b> —— 只管开事务、协调 DAO、抛异常，不知道 DispatchOutcome 是什么；</li>
 *   <li><b>实体</b> —— 知道自己有没有被冻结、加完钱是多少（充血模型）。</li>
 * </ul>
 * 把加锁写进每个 handler 也能跑，但那样「忘了释放锁」和「锁键拼错」就有四份机会发生。
 */
@Slf4j
public abstract class AbstractAssetHandler implements IAssetHandler {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 【模板方法】标准的加锁骨架，声明为 final，严禁子类重写！
     */
    @Override
    public final DispatchOutcome dispatch(ProposalRecord proposal) {
        // 1. 获取子类定义的锁 Key
        String lockKey = getLockKey(proposal);
        if (lockKey == null) {
            // 预留后路：如果某个资产不需要加锁（比如发优惠券），直接执行
            return executeWithLock(proposal);
        }

        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 2. 尝试加锁
            boolean isLocked = lock.tryLock(3, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("【并发拦截】未获取到资产操作锁，提案ID: {}", proposal.getId());
                return DispatchOutcome.failed("系统繁忙，请稍后再试");
            }

            // 3. 【绝杀钩子】锁获取成功，调用子类的具体路由逻辑
            return executeWithLock(proposal);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【系统异常】获取锁被中断，提案ID: {}", proposal.getId());
            return DispatchOutcome.failed("操作被中断");
        } finally {
            // 4. 标准化释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 【必须实现】定义当前资产域的防并发锁 Key
     */
    protected abstract String getLockKey(ProposalRecord proposal);

    /**
     * 【必须实现】在锁的保护下，执行真实的资产路由调用
     */
    protected abstract DispatchOutcome executeWithLock(ProposalRecord proposal);
}