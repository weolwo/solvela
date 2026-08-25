package solvela.task.runtime.strategy;

import solvela.task.constant.TaskTypeEnum;
import solvela.task.record.domain.entity.TaskRecord;
import solvela.task.runtime.domain.MetricPlan;
import solvela.task.runtime.domain.TaskEventContext;
import solvela.task.runtime.domain.TaskRuleConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 单次节点型：一次性达成，0 -> 1。如「完善资料」「首次下单」。
 *
 * <p>与 COUNT 的差别只在于<b>目标恒为 1</b>，累积方式是一样的，
 * 所以这里不做特殊处理，让它自然走 {@code targetCount} 判定
 * （模板配 {@code targetCount:1} 即可；未配时由 isCompleted 兜底为达标）。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Component
public class SimpleTaskStrategy implements TaskProgressStrategy {

    private static final BigDecimal SIMPLE_TARGET = BigDecimal.ONE;

    @Override
    public TaskTypeEnum supportType() {
        return TaskTypeEnum.SIMPLE;
    }

    @Override
    public MetricPlan plan(TaskRecord record, TaskEventContext ctx, TaskRuleConfig rule) {
        return new MetricPlan.Accumulate(BigDecimal.ONE);
    }

    /**
     * 目标恒为 1：模板没配 targetCount 时也必须能达标。
     *
     * <p>若照默认实现走 {@code rule.target()}，未配置时 target 为 null、
     * {@code reached} 返回 false，表现是「点了但永远不完成」——
     * 对一次性任务这是最容易漏配的一格，所以在这里兜住。
     */
    @Override
    public boolean isCompleted(BigDecimal metric, TaskRuleConfig rule) {
        BigDecimal target = rule.target() == null ? SIMPLE_TARGET : rule.target();
        return rule.reached(metric, target);
    }
}
