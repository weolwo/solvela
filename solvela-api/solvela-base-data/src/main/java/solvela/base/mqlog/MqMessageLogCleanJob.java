package solvela.base.mqlog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;

import java.time.LocalDateTime;

/**
 * 消息接收记录清理：只保留最近 N 天。
 *
 * <h3>为什么必须有人清</h3>
 * 这张表是<b>每条消息每个消费者一行</b>。活动事件那套上线之后，一条会员登录消息
 * 会被订阅它的每个活动各记一行 —— 量是「消息量 × 订阅者数」。
 * 没有清理任务的话它只会一直涨，而涨到影响写入之前<b>不会有任何症状</b>。
 *
 * <h3>只删成功的</h3>
 * 🔴 处理失败的行<b>不删</b>，不管多久。它们是「这条消息没处理成功」的唯一证据，
 * 删掉就等于把问题抹平了 —— 而保留期到了还没被处理的失败，恰恰最需要有人看见。
 * 失败堆积说明下游一直有问题，那是要告警的，不是要清理的。
 *
 * <p>幂等：条件带着时间与状态，重复跑第二遍影响行数就是 0。
 *
 * <p>⚠️ 时间取 {@link SolvelaJobContext#dbNow()} 而不是 JVM 的时钟：
 * 两个时钟差几小时的话，删掉的就是<b>不该删</b>的那一批 —— 而且不可逆。
 *
 * @Date 2026-08-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "mqMessageLogClean",
        title = "【基础】消息接收记录清理",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 300,
        params = {
                @JobParam(key = "retainDays", desc = "保留天数，默认 7",
                        type = JobParam.Type.INT, defaultValue = "7"),
                @JobParam(key = "dryRun", desc = "试运行：只统计将要删除的行数，不删数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class MqMessageLogCleanJob implements SolvelaJob {

    /**
     * 单批删除条数。分批不是性能优化 —— 一次 DELETE 掉几十万行会长时间持有行锁、
     * 撑爆 binlog，一个清理任务不该有能力影响线上业务。
     */
    private static final int BATCH_SIZE = 1000;

    /** 单次执行最多跑多少批。清不完下次接着清 —— 反正它是幂等的 */
    private static final int MAX_BATCH_ROUND = 50;

    private final MqMessageLogService mqMessageLogService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        int retainDays = ctx.intParam("retainDays", 7);
        LocalDateTime deadline = ctx.dbNow().minusDays(retainDays);

        if (ctx.boolParam("dryRun", false)) {
            long cleanable = mqMessageLogService.countCleanable(deadline);
            log.info("【消息记录清理】试运行：{} 之前已成功处理的记录有 {} 行", deadline, cleanable);
            return "试运行：有 " + cleanable + " 行可清理，本次未删除任何数据";
        }

        int total = 0;
        for (int round = 0; round < MAX_BATCH_ROUND; round++) {
            // 超时靠中断实现，每批开头自查一次，否则超时配了也砍不掉
            ctx.checkCancelled();
            int rows = mqMessageLogService.cleanBatch(deadline, BATCH_SIZE);
            total += rows;
            if (rows > 0) {
                // 卡住时这一行是唯一的线索：返回值只有跑完才有
                log.info("【消息记录清理】第 {} 批删除 {} 行，累计 {} 行", round + 1, rows, total);
            }
            if (rows < BATCH_SIZE) {
                break;
            }
        }

        if (total == 0) {
            return "没有需要清理的记录";
        }
        return "已清理 " + total + " 行（保留 " + retainDays + " 天，失败的记录一律保留）";
    }
}
