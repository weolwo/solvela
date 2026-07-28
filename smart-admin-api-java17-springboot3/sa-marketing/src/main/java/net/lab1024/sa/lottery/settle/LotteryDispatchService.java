package net.lab1024.sa.lottery.settle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.lottery.config.domain.entity.LotteryConfig;
import net.lab1024.sa.lottery.config.service.LotteryConfigService;
import net.lab1024.sa.lottery.record.dao.LotteryRecordDao;
import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 中奖派奖：把核销出的中奖记录投递进公共派发链路。
 *
 * <h3>为什么与核销分开，而不是核销时顺手 publish</h3>
 * 一期可能有几千个中奖者。{@code GlobalEventDispatcher.dispatch} 现在挂着 {@code @Async}
 * 走有界线程池，在核销事务里逐条 publish 会直接把队列打满。
 * 拆成独立的分页任务后，投递节奏可控，核销事务也短。
 *
 * <h3>为什么要 dispatch_status 列，而不是靠唯一索引拦重复</h3>
 * {@code t_prize_log.uk_external_biz} 确实能拦住重复派发，但那是<b>基于异常</b>的去重 ——
 * 每次重扫都要触发一次异常路径。本项目已经证明过这种做法会掩盖真问题
 * （v3.36 那次「防重索引压根没建、catch 一直在空转」）。
 * 用一个显式状态列，扫描直接跳过已投递的，唯一索引退居兜底。
 *
 * <h3>派发链路复用抽奖已压测通过的那一条</h3>
 * {@code UserPrizeEvent -> GlobalEventDispatcher -> PrizeDispatchHandler -> 提案域 -> 账务域}。
 * {@code sourceBizId} 取 {@code t_lottery_record.id} —— {@link UserPrizeEvent} 的字段注释
 * 原文就是「来源单号（LotteryRecord的ID）」，这个契约本来就是为彩票预留的。
 *
 * <h3>循环在这里，事务在 {@link LotteryDispatchBatchService}</h3>
 * 拆两个 Bean 不是洁癖：{@code @Transactional} 靠 Spring AOP 代理生效，
 * <b>同类内部的自调用不经过代理</b>，写在一个类里注解会静默失效 ——
 * 没事务、不报错、编译和测试全过，只在「标记了已投递但事件没发出去」时才暴露。
 * 领号链路的 TicketPersistService 是同样的理由。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LotteryDispatchService {

    private final LotteryConfigService lotteryConfigService;
    private final LotteryRecordDao lotteryRecordDao;
    private final LotteryDispatchBatchService lotteryDispatchBatchService;

    /**
     * 单批投递量。批太大会瞬间压满异步线程池，太小则往返次数多
     */
    private static final int BATCH_SIZE = 200;

    private static final int MAX_BATCH_ROUNDS = 5000;

    /**
     * 派发某一期的全部中奖记录，返回本次投递条数
     */
    public int dispatchIssue(String lotteryCode, String issueNo) {
        LotteryConfig config = lotteryConfigService.getByLotteryCode(lotteryCode);
        if (config == null) {
            log.error("[彩票派奖] 玩法不存在，跳过：{}", lotteryCode);
            return 0;
        }
        int total = 0;
        for (int round = 0; round < MAX_BATCH_ROUNDS; round++) {
            List<LotteryRecord> batch = lotteryRecordDao.selectPendingDispatch(lotteryCode, issueNo, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            total += lotteryDispatchBatchService.dispatchBatch(config, batch);
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        log.info("[彩票派奖] 期号 {} 本次投递 {} 条", issueNo, total);
        return total;
    }

}
