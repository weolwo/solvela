package solvela.lottery.settle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.domain.event.UserPrizeEvent;
import solvela.lottery.config.domain.entity.LotteryConfig;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.record.domain.entity.LotteryRecord;
import solvela.prize.prizeconfig.domain.entity.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 派奖的单批事务单元，单独成 Bean。
 *
 * <p><b>为什么不能内联回 {@link LotteryDispatchService}</b>：
 * {@code @Transactional} 靠 Spring AOP 代理生效，同类内部的自调用不经过代理，
 * 注解会静默失效。而这里的事务不是可有可无 ——
 * {@code PrizeDispatchHandler} 挂在 {@code @TransactionalEventListener(AFTER_COMMIT)} 上，
 * <b>没有事务上下文时事件根本不会投递出去</b>，
 * 表现就是「记录都标成已投递了，但下游一条都没收到」。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LotteryDispatchBatchService {

    private final LotteryRecordDao lotteryRecordDao;
    private final PrizeConfigService prizeConfigService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final int DISPATCH_FAIL = 2;

    /**
     * 一批的投递 + 标记，单事务。
     *
     * <p>事件是在事务内 publish 的，这是必须的：{@code PrizeDispatchHandler} 挂在
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 上，没有事务上下文时事件根本不会投递出去。
     * 同时 AFTER_COMMIT 也保证了「标记为已投递」与「真的投递」不会一个成功一个失败。
     */
    @Transactional(rollbackFor = Exception.class)
    public int dispatchBatch(LotteryConfig config, List<LotteryRecord> batch) {
        List<Long> dispatchedIds = new ArrayList<>(batch.size());
        for (LotteryRecord record : batch) {
            // prize_code 是核销时快照进记录的，这里不回查规则表 —— 规则可能已被改动
            PrizeConfig prize = prizeConfigService.getByActivityCodeAndPrizeCode(
                    config.getActivityCode(), record.getPrizeCode());
            if (prize == null) {
                // 奖品被删：标记为投递失败而不是反复重试，让它在报表里可见
                log.error("[彩票派奖] 奖品配置不存在，记录 {} 标记为投递失败：activityCode={}, prizeCode={}",
                        record.getId(), config.getActivityCode(), record.getPrizeCode());
                LotteryRecord fail = new LotteryRecord();
                fail.setId(record.getId());
                fail.setDispatchStatus(DISPATCH_FAIL);
                lotteryRecordDao.updateById(fail);
                continue;
            }
            applicationEventPublisher.publishEvent(UserPrizeEvent.builder()
                    // 跨域幂等键：配合 t_prize_log.uk_external_biz，事件重投也不会重复发奖
                    .sourceBizId(String.valueOf(record.getId()))
                    .activityCode(config.getActivityCode())
                    .memberId(record.getMemberId())
                    .memberName(record.getMemberName())
                    .prizeCode(record.getPrizeCode())
                    .prizeType(prize.getPrizeType())
                    .prizeValue(prize.getPrizeValue() == null ? null : prize.getPrizeValue().toPlainString())
                    .prizeName(prize.getPrizeName())
                    .prizeLevel(record.getPrizeLevel())
                    .build());
            dispatchedIds.add(record.getId());
        }
        if (!dispatchedIds.isEmpty()) {
            lotteryRecordDao.markDispatched(dispatchedIds);
        }
        return dispatchedIds.size();
    }
}
