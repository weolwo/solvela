package net.lab1024.sa.task.runtime.strategy;

import net.lab1024.sa.task.constant.TaskTypeEnum;
import net.lab1024.sa.task.record.domain.entity.TaskRecord;
import net.lab1024.sa.task.runtime.domain.MetricPlan;
import net.lab1024.sa.task.runtime.domain.TaskEventContext;
import net.lab1024.sa.task.runtime.domain.TaskRuleConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 计次型：每次事件 +1。如「累计签到 7 天」「下单 3 次」。
 *
 * <p>走原子累加 —— 事件的重复投递由 t_task_record_flow 的唯一索引挡在更外层，
 * 所以到这里的每一个事件都是「确实该算一次」的。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Component
public class CountTaskStrategy implements TaskProgressStrategy {

    @Override
    public TaskTypeEnum supportType() {
        return TaskTypeEnum.COUNT;
    }

    @Override
    public MetricPlan plan(TaskRecord record, TaskEventContext ctx, TaskRuleConfig rule) {
        return new MetricPlan.Accumulate(BigDecimal.ONE);
    }
}
