package solvela.lottery.settle;

import solvela.enums.ActivityTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.event.UserPrizeEvent;
import solvela.lottery.LotteryConfig;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.LotteryRecord;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.dispatch.outbox.PrizeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final PrizeEventPublisher prizeEventPublisher;

    /**
     * 一批的投递 + 标记，单事务。
     *
     * <p>事件是在事务内 publish 的，这是必须的：{@code PrizeDispatchHandler} 挂在
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 上，没有事务上下文时事件根本不会投递出去。
     * 同时 AFTER_COMMIT 也保证了「标记为已投递」与「真的投递」不会一个成功一个失败。
     */
    @Transactional(rollbackFor = Exception.class)
    public int dispatchBatch(LotteryConfig config, List<LotteryRecord> batch) {
        // 整批的奖品配置一次查完。改造前是逐条查，一批 500 条就是 500 次往返，
        // 而这是运行态派奖路径上的循环
        Map<String, PrizeConfig> prizeMap = prizeConfigService.mapByActivityCodeAndPrizeCodes(
                config.getActivityCode(),
                batch.stream().map(LotteryRecord::getPrizeCode).toList());

        List<Long> dispatchedIds = new ArrayList<>(batch.size());
        for (LotteryRecord record : batch) {
            // prize_code 是核销时快照进记录的，这里不回查规则表 —— 规则可能已被改动
            PrizeConfig prize = prizeMap.get(record.getPrizeCode());
            if (prize == null) {
                markDispatchFailed(config, record);
                continue;
            }
            publishPrizeEvent(config, record, prize);
            dispatchedIds.add(record.getId());
        }
        if (!dispatchedIds.isEmpty()) {
            lotteryRecordDao.markDispatched(dispatchedIds);
        }
        return dispatchedIds.size();
    }

    /**
     * 奖品配置被删：标记为投递失败，<b>不反复重试</b>。
     *
     * <p>重试解决不了「配置没了」这件事，只会让这条记录每一轮都被捞出来再失败一次。
     * 标成失败它才会出现在报表里，运营补回配置之后可以人工重投。
     */
    private void markDispatchFailed(LotteryConfig config, LotteryRecord record) {
        log.error("[彩票派奖] 奖品配置不存在，记录 {} 标记为投递失败：activityCode={}, prizeCode={}",
                record.getId(), config.getActivityCode(), record.getPrizeCode());
        // 条件更新：只有「中奖且待派发」才标失败。0 行说明别的批次已经处理过这条，不覆盖
        if (lotteryRecordDao.markDispatchFailed(record.getId()) == 0) {
            log.warn("[彩票派奖] 记录 {} 已不在待派发状态，本次不标失败", record.getId());
        }
    }

    /**
     * 发一条发奖事件。
     *
     * <p>奖品的静态信息（类型/名称/价值）取<b>配置</b>，中奖的动态信息（是谁、几等奖）
     * 取<b>记录</b>。反过来取会把彩票按中奖等级算出的东西抹平成配置里的基准值。
     */
    private void publishPrizeEvent(LotteryConfig config, LotteryRecord record, PrizeConfig prize) {
        prizeEventPublisher.publish(UserPrizeEvent.builder()
                // 跨域幂等键：配合 t_prize_log.uk_external_biz，事件重投也不会重复发奖
                .sourceBizId(String.valueOf(record.getId()))
                .activityType(ActivityTypeEnum.LOTTERY)
                .activityCode(config.getActivityCode())
                .memberId(record.getMemberId())
                .memberName(record.getMemberName())
                .prizeCode(record.getPrizeCode())
                .prizeType(prize.getPrizeType())
                .prizeValue(prize.getPrizeValue() == null ? null : prize.getPrizeValue().toPlainString())
                .prizeName(prize.getPrizeName())
                .prizeLevel(record.getPrizeLevel())
                .build());
    }
}
