package net.lab1024.sa.task.runtime.strategy;

import net.lab1024.sa.task.constant.TaskDiscardCode;
import net.lab1024.sa.task.constant.TaskTypeEnum;
import net.lab1024.sa.task.record.domain.entity.TaskRecord;
import net.lab1024.sa.task.runtime.TaskPeriodResolver;
import net.lab1024.sa.task.runtime.domain.MetricPlan;
import net.lab1024.sa.task.runtime.domain.TaskEventContext;
import net.lab1024.sa.task.runtime.domain.TaskProgressData;
import net.lab1024.sa.task.runtime.domain.TaskRuleConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 连续型：断档清零。如「连续签到 7 天」。
 *
 * <p>🔴 <b>它与 COUNT 不只是「多记一个 lastHitDate」的差别，周期模型根本不同。</b>
 * STREAK 恒用 {@code period_key = NONE}（整个周期一条记录，连续数才有地方累加）——
 * 若按天分片，每天都是一条新记录、{@code current_metric} 永远是 1，「已连续 5 天」无处可存。
 * 代价是 {@code uk_t_tsk_rec_mbr_cfg_prd} 不再承担日内幂等，改由 {@code t_task_record_flow} 承担。
 * 详见方案 §2.1.1。
 *
 * <p>这也是四种类型里<b>唯一</b>走读-改-写 + 乐观锁的：断档要清零，
 * 必须先读到 lastHitDate 才知道本次该算 1 还是 current+1，一条累加 SQL 表达不了。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Component
public class StreakTaskStrategy implements TaskProgressStrategy {

    @Override
    public TaskTypeEnum supportType() {
        return TaskTypeEnum.STREAK;
    }

    @Override
    public MetricPlan plan(TaskRecord record, TaskEventContext ctx, TaskRuleConfig rule) {
        TaskProgressData progress = TaskProgressData.parse(record.getProgressData());
        String today = TaskPeriodResolver.formatDay(ctx.eventTime());
        String lastHitDate = progress.lastHitDate();

        BigDecimal current = record.getCurrentMetric() == null ? BigDecimal.ZERO : record.getCurrentMetric();
        BigDecimal next;

        if (lastHitDate == null) {
            // 首次命中
            next = BigDecimal.ONE;
        } else {
            long gapDays = daysBetween(lastHitDate, today);
            if (gapDays <= 0) {
                // 同一天（或事件时间倒流）。正常情况下流水表的唯一索引已经挡在外层，
                // 这里是防御性分支：即便幂等被绕过，也不能让同一天签两次算成连续两天。
                return new MetricPlan.Skip(TaskDiscardCode.STREAK_SAME_DAY,
                        "当日已计入连续进度（" + today + "），不重复累加");
            }
            // 允许断档 tolerance 次：间隔 1 天是「连上了」，间隔 tolerance+1 天仍在容忍范围内
            next = gapDays <= rule.tolerance() + 1L ? current.add(BigDecimal.ONE) : BigDecimal.ONE;
            // ⚠️ 断档是「归零再 +1」而不是「归零」—— 断档当天本身也是有效的一次。
            //    这是连续型任务最经典的 off-by-one，别改成 BigDecimal.ZERO。
        }

        String progressJson = progress.withLastHitDate(today).toJson();
        return new MetricPlan.Overwrite(next, progressJson, record.getVersion());
    }

    /**
     * 两个 yyyyMMdd 之间相差的自然天数。
     *
     * <p>脏数据（lastHitDate 格式不对）时返回一个大于任何 tolerance 的值，
     * 效果是「当作断档、从 1 重新开始」—— 宁可让用户重新连，不能凭空送一个连续数。
     */
    private long daysBetween(String fromDay, String toDay) {
        try {
            LocalDate from = LocalDate.parse(fromDay, TaskPeriodResolver.DAY_FORMAT);
            LocalDate to = LocalDate.parse(toDay, TaskPeriodResolver.DAY_FORMAT);
            return ChronoUnit.DAYS.between(from, to);
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }
}
