package solvela.task.runtime.strategy;

import solvela.task.constant.TaskDiscardCode;
import solvela.task.constant.TaskTypeEnum;
import solvela.task.record.domain.entity.TaskRecord;
import solvela.task.runtime.domain.MetricPlan;
import solvela.task.runtime.domain.TaskEventContext;
import solvela.task.runtime.domain.TaskRuleConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 计额型：每次事件 + event.amount。如「累计消费满 500」。
 *
 * <p>两个丢弃分支<b>都必须给人话原因</b> —— 它们正是「用户下了 99 元的单为什么没进度」
 * 这类客诉的答案，原因会原样落进 {@code t_task_record_flow.discard_reason}。
 * 没有这个，中台就只能靠翻日志自证，用几次就没人敢用了。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Component
public class AmountTaskStrategy implements TaskProgressStrategy {

    @Override
    public TaskTypeEnum supportType() {
        return TaskTypeEnum.AMOUNT;
    }

    @Override
    public MetricPlan plan(TaskRecord record, TaskEventContext ctx, TaskRuleConfig rule) {
        BigDecimal amount = ctx.amount();
        if (amount.signum() <= 0) {
            return new MetricPlan.Skip(TaskDiscardCode.AMOUNT_MISSING,
                    "事件未携带有效金额（amount=" + amount.toPlainString() + "）");
        }

        // 单笔门槛：如「单笔满 100 才计入累计」。未配置则不限
        BigDecimal minAmount = rule.minAmount();
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            return new MetricPlan.Skip(TaskDiscardCode.AMOUNT_BELOW_MIN,
                    "单笔金额 " + amount.toPlainString()
                            + " 未达门槛 " + minAmount.toPlainString() + "，本次不计入累计");
        }

        return new MetricPlan.Accumulate(amount);
    }
}
