package net.lab1024.sa.ledger.handler;

import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.AssetStrategy;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.wallet.service.MemberWalletService;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 现金/余额派发核心底层执行器
 */
@AllArgsConstructor
@Slf4j
@Service
@AssetStrategy(PrizeTypeEnum.BALANCE)
public class WalletAssetHandler implements IAssetHandler {

    private final MemberWalletService memberWalletService;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public ResponseDTO dispatch(ProposalRecord proposal) {
        // 1. 定义锁的粒度：精确到具体的某个人
        String lockKey = "lock:wallet_update:" + proposal.getMemberName();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 2. 尝试加锁 (最多等待 3 秒，锁的租期由 Watchdog 自动续期)
            boolean isLocked = lock.tryLock(3, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("【并发拦截】未获取到资产操作锁，提案ID: {}", proposal.getId());
                // 被锁挡住了，说明前面有个请求正在处理。这里可以直接报错让上层重试，
                // 或者直接返回操作频繁。
                return ResponseDTO.userErrorParam("系统繁忙，请稍后再试");
            }

            // ==========================================
            // 3. 【绝杀技巧】调用带有 @Transactional 的内部方法
            // ==========================================
            return memberWalletService.doDispatch(proposal);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseDTO.userErrorParam("操作被中断");
        } finally {
            // 4. 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


}