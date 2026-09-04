package solvela.admin.module.system.job.bootstrap;

import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.job.alarm.SolvelaJobAlarmSender;
import solvela.admin.module.system.job.config.SolvelaJobConfig;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.admin.module.system.job.repository.SolvelaJobRepository;
import solvela.admin.module.system.job.repository.domain.SolvelaJobEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内置任务：调度健康自检。
 *
 * <p>🔴 <b>三条告警里最容易被跳过、却唯一有用的那条。</b>
 *
 * <p>「连续失败」和「执行超时」都建立在<b>任务确实跑了</b>这个前提上 ——
 * 它们能告诉你任务跑错了，但调度线程本身死掉时，系统会<b>安静如常</b>：
 * 没有异常、没有错误日志、后台列表一切正常，只是什么都不再发生。
 * 运营发现问题的方式通常是三天后有人投诉「奖怎么没发」。
 *
 * <p>判据是「有任务的 {@code next_trigger_time} 早于阈值却还没被抢走」。
 * 抢占式调度让这件事第一次可查 —— 内存 Trigger 模型里根本没有这样一个可观测点。
 *
 * <p>⚠️ 它自己也是一个定时任务，所以存在「自己挂了就没人报警」的盲区。
 * 这是可接受的：它跑在快车道、逻辑极简（一条索引查询），
 * 与它一起挂掉的必然是整个扫描器，而那时更外层的进程存活监控会发现。
 * <b>真正要防的是「扫描器活着但某些任务被漏掉」</b>，那种情况这里能抓到。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
@Component
@SolvelaJobHandler(
        name = "_jobHealthCheck",
        title = "【系统】调度健康自检与告警",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 30
)
public class SolvelaJobHealthCheckHandler implements SolvelaJob {

    private final SolvelaJobRepository jobRepository;

    private final SolvelaJobConfig jobConfig;

    private final SolvelaJobAlarmSender alarmSender;

    public SolvelaJobHealthCheckHandler(SolvelaJobRepository jobRepository, SolvelaJobConfig jobConfig,
                                        SolvelaJobAlarmSender alarmSender) {
        this.jobRepository = jobRepository;
        this.jobConfig = jobConfig;
        this.alarmSender = alarmSender;
    }

    /**
     * 三类体检，每类命中就发一条告警，最后汇总成一句话回给执行记录。
     *
     * <p>三类回答的是不同的问题：<b>调度器还活着吗</b>（超期未触发）、
     * <b>任务在跑但一直失败吗</b>（连续失败）、<b>有任务永远跑不起来吗</b>（执行器失联）。
     * 前者是系统级故障，后两者是单个任务的问题 —— 收件人不同，所以不合并成一条。
     */
    @Override
    public String execute(SolvelaJobContext ctx) {
        // 一次查库，两类体检共用。原来这里查了两遍全表
        List<SolvelaJobEntity> liveJobs = jobRepository.getJobDao().selectList(null).stream()
                .filter(e -> !Boolean.TRUE.equals(e.getDeletedFlag()))
                .toList();

        String summary = checkOverdue(ctx) + checkContinuousFail(liveJobs) + checkHandlerMissing(liveJobs);
        return summary.isEmpty() ? "调度健康，无异常" : summary;
    }

    /** ① 超期未触发 —— 说明调度器没在正常工作 */
    private String checkOverdue(SolvelaJobContext ctx) {
        LocalDateTime overdueTime = ctx.dbNow().minusSeconds(jobConfig.getAlarmOverdueSeconds());
        List<SolvelaJobEntity> overdueList = jobRepository.getJobDao()
                .selectOverdueJobList(jobConfig.getEnv(), overdueTime);
        return raise(SolvelaJobAlarmSender.Type.SCHEDULER_DOWN, overdueList,
                String.format("调度可能已停摆：%d 个任务超过 %d 秒未被触发",
                        overdueList.size(), jobConfig.getAlarmOverdueSeconds()),
                e -> e.getJobName() + "(应触发于 " + e.getNextTriggerTime() + ")",
                "超期未触发");
    }

    /** ② 连续失败 —— 任务在跑但一直失败 */
    private String checkContinuousFail(List<SolvelaJobEntity> liveJobs) {
        List<SolvelaJobEntity> failingList = liveJobs.stream()
                .filter(e -> Boolean.TRUE.equals(e.getEnabledFlag()))
                .filter(e -> null != e.getContinuousFailCount()
                        && e.getContinuousFailCount() >= jobConfig.getAlarmContinuousFailTimes())
                .toList();
        return raise(SolvelaJobAlarmSender.Type.JOB_CONTINUOUS_FAIL, failingList,
                String.format("%d 个任务连续失败达到 %d 次", failingList.size(),
                        jobConfig.getAlarmContinuousFailTimes()),
                e -> e.getJobName() + "(连续失败 " + e.getContinuousFailCount() + " 次)",
                "连续失败");
    }

    /** ③ handler 失联 —— 配了但代码里没有，这些任务永远不会执行 */
    private String checkHandlerMissing(List<SolvelaJobEntity> liveJobs) {
        List<SolvelaJobEntity> missingList = liveJobs.stream()
                .filter(e -> Boolean.TRUE.equals(e.getHandlerMissingFlag()))
                .toList();
        return raise(SolvelaJobAlarmSender.Type.HANDLER_MISSING, missingList,
                String.format("%d 个任务的执行器在代码中不存在，它们永远不会被执行", missingList.size()),
                e -> e.getJobName() + "(handler=" + e.getHandlerName() + ")",
                "执行器失联");
    }

    /**
     * 命中就发一条告警，并返回汇总里的那一小段；没命中返回空串。
     *
     * @param describe 每个任务在告警明细里怎么写。三类告警要说的重点不同 ——
     *                 超期要说「应该什么时候触发」，连续失败要说「失败了几次」
     * @return 形如「连续失败 3 个；」，直接拼进执行记录的 result_summary
     */
    private String raise(SolvelaJobAlarmSender.Type type, List<SolvelaJobEntity> jobs, String title,
                         Function<SolvelaJobEntity, String> describe, String summaryLabel) {
        if (jobs.isEmpty()) {
            return "";
        }
        String detail = jobs.stream().map(describe).collect(Collectors.joining("; "));
        alarmSender.send(type, title, detail, this.pickReceiver(jobs));
        return summaryLabel + " " + jobs.size() + " 个；";
    }

    /**
     * 取第一个配了接收人的任务作为告警接收方。
     *
     * <p>刻意不做「按任务分别发给各自接收人」：那会把一次批量异常炸成 N 条告警，
     * 而这三类告警本质都是<b>系统级</b>的（调度器有没有在正常工作），
     * 收件人需要的是一条汇总，不是一堆碎片
     */
    private String pickReceiver(List<SolvelaJobEntity> jobList) {
        return jobList.stream()
                .map(SolvelaJobEntity::getAlarmReceiver)
                .filter(e -> null != e && !e.isBlank())
                .findFirst()
                .orElse(null);
    }
}
