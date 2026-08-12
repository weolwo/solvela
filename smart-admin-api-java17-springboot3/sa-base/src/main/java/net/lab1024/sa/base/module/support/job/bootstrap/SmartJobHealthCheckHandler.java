package net.lab1024.sa.base.module.support.job.bootstrap;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.module.support.job.alarm.SmartJobAlarmSender;
import net.lab1024.sa.base.module.support.job.config.SmartJobConfig;
import net.lab1024.sa.base.module.support.job.constant.SmartJobLaneEnum;
import net.lab1024.sa.base.module.support.job.core.SmartJob;
import net.lab1024.sa.base.module.support.job.core.SmartJobContext;
import net.lab1024.sa.base.module.support.job.core.SmartJobHandler;
import net.lab1024.sa.base.module.support.job.repository.SmartJobRepository;
import net.lab1024.sa.base.module.support.job.repository.domain.SmartJobEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
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
@SmartJobHandler(
        name = "_jobHealthCheck",
        title = "【系统】调度健康自检与告警",
        group = "SYSTEM",
        lane = SmartJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 30
)
public class SmartJobHealthCheckHandler implements SmartJob {

    private final SmartJobRepository jobRepository;

    private final SmartJobConfig jobConfig;

    private final SmartJobAlarmSender alarmSender;

    public SmartJobHealthCheckHandler(SmartJobRepository jobRepository, SmartJobConfig jobConfig,
                                      SmartJobAlarmSender alarmSender) {
        this.jobRepository = jobRepository;
        this.jobConfig = jobConfig;
        this.alarmSender = alarmSender;
    }

    @Override
    public String execute(SmartJobContext ctx) {
        LocalDateTime overdueTime = ctx.dbNow().minusSeconds(jobConfig.getAlarmOverdueSeconds());
        List<SmartJobEntity> overdueList = jobRepository.getJobDao()
                .selectOverdueJobList(jobConfig.getEnv(), overdueTime);

        StringBuilder summary = new StringBuilder();

        // ① 超期未触发 —— 说明调度器没在正常工作
        if (!overdueList.isEmpty()) {
            String detail = overdueList.stream()
                    .map(e -> e.getJobName() + "(应触发于 " + e.getNextTriggerTime() + ")")
                    .collect(Collectors.joining("; "));
            alarmSender.send(SmartJobAlarmSender.Type.SCHEDULER_DOWN,
                    String.format("调度可能已停摆：%d 个任务超过 %d 秒未被触发",
                            overdueList.size(), jobConfig.getAlarmOverdueSeconds()),
                    detail, this.pickReceiver(overdueList));
            summary.append("超期未触发 ").append(overdueList.size()).append(" 个；");
        }

        // ② 连续失败 —— 任务在跑但一直失败
        List<SmartJobEntity> failingList = jobRepository.getJobDao().selectList(null).stream()
                .filter(e -> !Boolean.TRUE.equals(e.getDeletedFlag()))
                .filter(e -> Boolean.TRUE.equals(e.getEnabledFlag()))
                .filter(e -> null != e.getContinuousFailCount()
                        && e.getContinuousFailCount() >= jobConfig.getAlarmContinuousFailTimes())
                .toList();
        if (!failingList.isEmpty()) {
            String detail = failingList.stream()
                    .map(e -> e.getJobName() + "(连续失败 " + e.getContinuousFailCount() + " 次)")
                    .collect(Collectors.joining("; "));
            alarmSender.send(SmartJobAlarmSender.Type.JOB_CONTINUOUS_FAIL,
                    String.format("%d 个任务连续失败达到 %d 次", failingList.size(),
                            jobConfig.getAlarmContinuousFailTimes()),
                    detail, this.pickReceiver(failingList));
            summary.append("连续失败 ").append(failingList.size()).append(" 个；");
        }

        // ③ handler 失联 —— 配了但代码里没有，这些任务永远不会执行
        List<SmartJobEntity> missingList = jobRepository.getJobDao().selectList(null).stream()
                .filter(e -> !Boolean.TRUE.equals(e.getDeletedFlag()))
                .filter(e -> Boolean.TRUE.equals(e.getHandlerMissingFlag()))
                .toList();
        if (!missingList.isEmpty()) {
            String detail = missingList.stream()
                    .map(e -> e.getJobName() + "(handler=" + e.getHandlerName() + ")")
                    .collect(Collectors.joining("; "));
            alarmSender.send(SmartJobAlarmSender.Type.HANDLER_MISSING,
                    String.format("%d 个任务的执行器在代码中不存在，它们永远不会被执行", missingList.size()),
                    detail, this.pickReceiver(missingList));
            summary.append("执行器失联 ").append(missingList.size()).append(" 个；");
        }

        return summary.isEmpty() ? "调度健康，无异常" : summary.toString();
    }

    /**
     * 取第一个配了接收人的任务作为告警接收方。
     *
     * <p>刻意不做「按任务分别发给各自接收人」：那会把一次批量异常炸成 N 条告警，
     * 而这三类告警本质都是<b>系统级</b>的（调度器有没有在正常工作），
     * 收件人需要的是一条汇总，不是一堆碎片
     */
    private String pickReceiver(List<SmartJobEntity> jobList) {
        return jobList.stream()
                .map(SmartJobEntity::getAlarmReceiver)
                .filter(e -> null != e && !e.isBlank())
                .findFirst()
                .orElse(null);
    }
}
