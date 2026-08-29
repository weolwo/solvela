package solvela.ledger.handler;

import lombok.extern.slf4j.Slf4j;
import solvela.dispatch.DispatchOutcome;
import solvela.risk.ProposalRecord;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import jakarta.annotation.Resource;

import java.util.concurrent.TimeUnit;

/**
 * AbstractLockedAssetHandler：是个没感情的保安，只管加锁放行。
 * WalletAssetHandler：是个翻译官（Facade），负责把 Service 的异常翻译成 DispatchOutcome 返回给上一层的分发引擎。
 * MemberWalletService：是个纯粹的数据库协调者，只管开事务和抛异常。
 * MemberWallet (Entity)：是个有血有肉的领域专家，知道自己有没有被冻结，知道自己加完钱是多少。
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