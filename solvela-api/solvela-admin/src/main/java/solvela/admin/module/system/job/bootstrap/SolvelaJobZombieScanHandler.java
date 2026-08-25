package solvela.admin.module.system.job.bootstrap;

import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;
import solvela.base.module.support.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.support.jobspi.core.SolvelaJob;
import solvela.base.module.support.jobspi.core.SolvelaJobContext;
import solvela.base.module.support.jobspi.core.SolvelaJobHandler;
import solvela.admin.module.system.job.core.SolvelaJobHandlerMeta;
import solvela.admin.module.system.job.core.SolvelaJobHandlerRegistry;
import solvela.admin.module.system.job.repository.SolvelaJobRepository;
import solvela.admin.module.system.job.repository.domain.SolvelaJobEntity;
import solvela.admin.module.system.job.repository.domain.SolvelaJobLogEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 内置任务：回收僵尸执行记录。
 *
 * <p>🔴 <b>它挡的是 {@code catch(Throwable)} 挡不住的那部分。</b>
 * 执行器外层已经捕获了 Throwable，能覆盖 99% 的情况；但 OOM 之后连 {@code finally}
 * 里的写库都可能因为分不到内存而失败，进程被 {@code SIGKILL} 时更是一行都跑不到。
 * 那些情况下记录会永久停在 {@code RUNNING}。
 *
 * <p>不回收的后果不只是「有几条脏记录」：阻塞判定用 RUNNING 记录作为跨节点的事实来源，
 * 一条僵尸记录会让这个任务<b>永久判定为阻塞、再也不执行</b>，而后台看起来一切正常。
 *
 * <p>回收时若还有重试余额，会<b>沿用与执行器完全相同的那套重试状态机</b>
 * （插一条新的 PENDING），而不是另写一套恢复逻辑 ——
 * 一个状态机能表达的事不要写第二个，否则两套逻辑迟早在 {@code retry_seq} 的推进上打架。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
@Component
@SolvelaJobHandler(
        name = "_jobZombieScan",
        title = "【系统】回收僵尸执行记录",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 30
)
public class SolvelaJobZombieScanHandler implements SolvelaJob {

    /**
     * 判定僵尸的最小年龄：至少 10 分钟。
     *
     * <p>取「超时 × 2 与 10 分钟的较大者」——
     * 阈值给小了会把正在正常执行的长任务误杀成僵尸，那比不回收更糟
     */
    private static final long MIN_ZOMBIE_AGE_SECONDS = 600;

    private static final int SCAN_LIMIT = 200;

    private final SolvelaJobRepository jobRepository;

    private final SolvelaJobHandlerRegistry handlerRegistry;

    public SolvelaJobZombieScanHandler(SolvelaJobRepository jobRepository, SolvelaJobHandlerRegistry handlerRegistry) {
        this.jobRepository = jobRepository;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public String execute(SolvelaJobContext ctx) {
        LocalDateTime deadline = ctx.dbNow().minusSeconds(MIN_ZOMBIE_AGE_SECONDS);
        List<SolvelaJobLogEntity> zombieList = jobRepository.getJobLogDao().selectZombieList(deadline, SCAN_LIMIT);
        if (zombieList.isEmpty()) {
            return "没有发现僵尸记录";
        }

        int recovered = 0;
        int retryScheduled = 0;
        for (SolvelaJobLogEntity zombie : zombieList) {
            ctx.checkCancelled();
            SolvelaJobEntity job = jobRepository.getJobDao().selectById(zombie.getJobId());
            // 二次确认年龄：selectZombieList 用的是统一阈值，这里按该任务自己的超时再核一遍，
            // 避免把「超时 60 分钟的对账任务」在第 10 分钟就判成僵尸
            if (null != job && !this.isZombie(job, zombie, ctx.dbNow())) {
                continue;
            }

            SolvelaJobLogEntity update = new SolvelaJobLogEntity();
            update.setLogId(zombie.getLogId());
            update.setStatus(SolvelaJobExecuteStatusEnum.INTERRUPTED.getValue());
            update.setExecuteEndTime(ctx.dbNow());
            update.setErrorDetail("疑似节点异常退出或进程被强杀，执行记录长期停留在「执行中」，由僵尸扫描回收");

            SolvelaJobLogEntity retry = this.buildRetry(job, zombie, ctx.dbNow());
            jobRepository.finishAndScheduleRetry(update, retry,
                    zombie.getJobId(), 1, zombie.getLogId());
            recovered++;
            if (null != retry) {
                retryScheduled++;
            }
            log.warn("==== SolvelaJob ==== 回收僵尸记录 logId={} job={} 开始于 {}",
                    zombie.getLogId(), zombie.getJobName(), zombie.getExecuteStartTime());
        }
        return String.format("回收僵尸记录 %d 条，其中安排重试 %d 条", recovered, retryScheduled);
    }

    private boolean isZombie(SolvelaJobEntity job, SolvelaJobLogEntity log, LocalDateTime dbNow) {
        Optional<SolvelaJobHandlerMeta> handler = handlerRegistry.getHandler(job.getHandlerName());
        int timeout = null == job.getTimeoutSeconds() || job.getTimeoutSeconds() <= 0
                ? handler.map(SolvelaJobHandlerMeta::defaultTimeoutSeconds).orElse(300)
                : job.getTimeoutSeconds();
        long ageSeconds = java.time.Duration.between(log.getExecuteStartTime(), dbNow).getSeconds();
        return ageSeconds > Math.max(MIN_ZOMBIE_AGE_SECONDS, timeout * 2L);
    }

    /**
     * 沿用与 {@code SolvelaJobRunner} 完全相同的重试约束，不另立一套
     */
    private SolvelaJobLogEntity buildRetry(SolvelaJobEntity job, SolvelaJobLogEntity zombie, LocalDateTime dbNow) {
        if (null == job) {
            return null;
        }
        int retryTimes = null == job.getRetryTimes() ? 0 : job.getRetryTimes();
        int currentSeq = null == zombie.getRetrySeq() ? 0 : zombie.getRetrySeq();
        if (retryTimes <= 0 || currentSeq >= retryTimes) {
            return null;
        }
        boolean idempotent = handlerRegistry.getHandler(job.getHandlerName())
                .map(SolvelaJobHandlerMeta::idempotent).orElse(false);
        if (!idempotent) {
            // 不幂等的任务不自动重试：它上次跑到哪儿谁也不知道，重来一遍等于两次副作用
            return null;
        }

        int interval = null == job.getRetryInterval() ? 30 : job.getRetryInterval();
        SolvelaJobLogEntity retry = new SolvelaJobLogEntity();
        retry.setJobId(zombie.getJobId());
        retry.setJobName(zombie.getJobName());
        retry.setAppEnv(zombie.getAppEnv());
        retry.setTriggerSource(zombie.getTriggerSource());
        retry.setTriggerTime(zombie.getTriggerTime());
        retry.setParamSnapshot(zombie.getParamSnapshot());
        retry.setBizDate(zombie.getBizDate());
        retry.setRetrySeq(currentSeq + 1);
        retry.setRetryOfLogId(zombie.getLogId());
        retry.setStatus(SolvelaJobExecuteStatusEnum.PENDING.getValue());
        retry.setFireTime(dbNow.plusSeconds(interval));
        retry.setCreateName("system");
        return retry;
    }
}
