package net.lab1024.sa.base.module.support.job.bootstrap;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.module.support.job.config.SmartJobConfig;
import net.lab1024.sa.base.module.support.job.constant.SmartJobLaneEnum;
import net.lab1024.sa.base.module.support.job.core.SmartJob;
import net.lab1024.sa.base.module.support.job.core.SmartJobContext;
import net.lab1024.sa.base.module.support.job.core.JobParam;
import net.lab1024.sa.base.module.support.job.core.SmartJobHandler;
import net.lab1024.sa.base.module.support.job.repository.SmartJobRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 内置任务：清理过期执行日志与终态的一次性任务。
 *
 * <p>调度器自举 —— 它自己的日志清理也由它自己跑。这也是<b>第一个验收用例</b>：
 * 这个任务能正常跑起来，说明抢占、投递、落状态整条链路是通的。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
@Component
@SmartJobHandler(
        name = "_jobLogClean",
        title = "【系统】清理定时任务执行日志",
        group = "SYSTEM",
        lane = SmartJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 600,
        params = {
                @JobParam(key = "retainDays", desc = "日志保留天数", type = JobParam.Type.INT,
                        defaultValue = "30")
        }
)
public class SmartJobLogCleanHandler implements SmartJob {

    /**
     * 单批删除条数。
     *
     * <p>🔴 分批不是性能优化，是必需：一次 DELETE 掉几十万行会长时间持有行锁、
     * 撑爆 binlog，还可能拖垮主从延迟 —— 一个清理任务不该有能力影响线上业务
     */
    private static final int BATCH_SIZE = 2000;

    /**
     * 单次执行最多删多少批，防止一次跑太久。删不完下次接着删
     */
    private static final int MAX_BATCH_ROUND = 50;

    private final SmartJobRepository jobRepository;

    private final SmartJobConfig jobConfig;

    public SmartJobLogCleanHandler(SmartJobRepository jobRepository, SmartJobConfig jobConfig) {
        this.jobRepository = jobRepository;
        this.jobConfig = jobConfig;
    }

    @Override
    public String execute(SmartJobContext ctx) {
        int retainDays = this.resolveRetainDays(ctx);
        // 🔴 用 ctx.dbNow() 而不是 LocalDateTime.now()（铁律 9/10）——
        //    删数据这种操作用错时钟，删掉的就是不该删的那一天
        LocalDateTime deadline = ctx.dbNow().minusDays(retainDays);

        int totalLog = 0;
        for (int i = 0; i < MAX_BATCH_ROUND; i++) {
            ctx.checkCancelled();
            int deleted = jobRepository.getJobLogDao().deleteBeforeTime(deadline, BATCH_SIZE);
            totalLog += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        // 顺带清掉终态的一次性任务：活动多了以后它们只增不减，会把列表淹掉
        int deletedJob = jobRepository.getJobDao().deleteTerminalOneTimeJob(deadline);

        return String.format("清理执行日志 %d 条（保留 %d 天，截止 %s），软删终态一次性任务 %d 个",
                totalLog, retainDays, deadline.toLocalDate(), deletedJob);
    }

    /**
     * 保留天数：任务参数优先，未配则取全局配置。
     *
     * <p>参数解析失败时<b>回退到配置值而不是抛异常</b>：一个填错的参数不该让清理任务彻底停摆，
     * 那会让日志表无声地涨下去
     */
    private int resolveRetainDays(SmartJobContext ctx) {
        int days = ctx.intParam("retainDays", 0);
        return days > 0 ? days : jobConfig.getLogRetainDays();
    }
}
