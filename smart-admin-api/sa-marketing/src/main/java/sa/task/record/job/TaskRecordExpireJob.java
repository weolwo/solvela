package sa.task.record.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sa.base.module.support.job.constant.SmartJobLaneEnum;
import sa.base.module.support.job.core.JobParam;
import sa.base.module.support.job.core.SmartJob;
import sa.base.module.support.job.core.SmartJobContext;
import sa.base.module.support.job.core.SmartJobHandler;
import sa.task.record.dao.TaskRecordDao;

import java.time.LocalDateTime;

/**
 * 任务记录过期收口：把过了有效期还停在「进行中」的记录置为 3-已过期。
 *
 * <h3>索引早就建好了，扫描一直没写</h3>
 * {@code idx_t_tsk_rec_expire(status, valid_end_time)} 这个索引<b>就是给这个扫描建的</b>，
 * 但扫描本身从来没有实现 —— 于是在这个任务之前，全工程没有任何地方把 status 改成 3。
 * 后果是记录永远不会自己收口：
 * <ul>
 *   <li>用户端会一直看到一个<b>永远完不成的任务</b>（有效期早过了，进度还停在那）；</li>
 *   <li>达标率的分母里一直混着这批僵尸记录，越积越多，指标越来越难看；</li>
 *   <li>任务漏斗里那个「已过有效期仍在进行中」的告警，量只会涨不会跌。</li>
 * </ul>
 *
 * <h3>只收口，不发奖、不补偿</h3>
 * 过期意味着这一轮没做完，本来就没有奖 —— 收口只改状态，不触发任何发奖或退还。
 * 反过来说也成立：<b>已完成/已发奖的记录不会被碰</b>，条件里只认 {@code status = 0}。
 *
 * <p>幂等，可配失败重试。时间取 {@link SmartJobContext#dbNow()}（铁律 9）。
 *
 * @author alaric
 * @date 2026-08-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SmartJobHandler(
        name = "taskRecordExpire",
        title = "【任务】任务记录过期收口",
        group = "BUSINESS",
        lane = SmartJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 300,
        params = {
                @JobParam(key = "dryRun", desc = "试运行：只统计将要过期的条数，不改数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class TaskRecordExpireJob implements SmartJob {

    /**
     * 单批更新条数。分批是为了不长时间持锁、不撑爆 binlog，与券收口同款
     */
    private static final int BATCH_SIZE = 1000;

    private static final int MAX_BATCH_ROUND = 50;

    private final TaskRecordDao taskRecordDao;

    @Override
    public String execute(SmartJobContext ctx) {
        LocalDateTime now = ctx.dbNow();

        if (ctx.boolParam("dryRun", false)) {
            long expirable = taskRecordDao.countExpirableRecord(now);
            log.info("【任务过期收口】试运行：截至 {} 有 {} 条记录已过有效期但仍在进行中", now, expirable);
            return "试运行：有 " + expirable + " 条记录已过有效期仍在进行中，本次未改动任何数据";
        }

        int total = 0;
        for (int round = 0; round < MAX_BATCH_ROUND; round++) {
            // 超时靠中断实现，每批开头自查一次（框架硬约束 1）
            ctx.checkCancelled();
            int rows = taskRecordDao.expireRecordBatch(now, BATCH_SIZE);
            total += rows;
            if (rows > 0) {
                log.info("【任务过期收口】第 {} 批收口 {} 条，累计 {} 条", round + 1, rows, total);
            }
            if (rows < BATCH_SIZE) {
                break;
            }
        }

        if (total == 0) {
            return "没有需要收口的任务记录";
        }
        log.info("【任务过期收口】本次共收口 {} 条（截至 {}）", total, now);
        return "已把 " + total + " 条过期任务记录置为「已过期」";
    }
}
