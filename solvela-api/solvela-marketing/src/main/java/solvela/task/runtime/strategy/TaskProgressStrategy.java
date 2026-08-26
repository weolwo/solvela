package solvela.task.runtime.strategy;

import solvela.task.constant.TaskTypeEnum;
import solvela.task.TaskRecord;
import solvela.task.runtime.domain.MetricPlan;
import solvela.task.runtime.domain.TaskEventContext;
import solvela.task.runtime.domain.TaskRuleConfig;

import java.math.BigDecimal;

/**
 * 任务进度推进策略（三层分离的第①层，方案 §3.2）。
 *
 * <p>第①层只回答一个问题：<b>进度如何累积</b>。这是唯一真正需要不同 Java 代码路径的维度。
 * 「目标是几次/几元」属于第②层 rule_config，「邀请 3 个新用户且他们都下过单」这类
 * 前两层表达不了的极端 case 才走第③层 rule_script。
 *
 * <p><b>实现必须是纯函数</b>：不查库、不写库、不依赖当前时间（时间从 ctx 取）。
 * SQL 由 {@code TaskRecordAdvanceService} 按返回的 {@link MetricPlan} 统一执行 ——
 * 这样并发语义只有一处，不会各策略各写一套。
 *
 * @author alaric
 * @date 2026-08-01
 */
public interface TaskProgressStrategy {

    /**
     * 本策略负责哪种 task_type。
     *
     * <p>⚠️ 装配用 {@code List} 注入 + 各实现自报，<b>不要</b> {@code Map<TaskTypeEnum, Strategy>}
     * —— 铁律 14。本项目已因「枚举键 Map 用字符串 get」踩过三次静默失效
     * （GlobalEventDispatcher / PrizeStrategyFactory / AssetStrategyFactory），
     * 三处都是编译通过、运行恒 null、异常被吞。
     */
    TaskTypeEnum supportType();

    /**
     * 算出本次事件该如何推进进度。
     *
     * @param record 当前任务记录（已存在；首次接取由调用方先建好）
     * @param ctx    事件上下文
     * @param rule   rule_config
     * @return 推进方案；条件不满足时返回 {@link MetricPlan.Skip} 并给出人话原因
     */
    MetricPlan plan(TaskRecord record, TaskEventContext ctx, TaskRuleConfig rule);

    /**
     * 是否已达标（按最终的 current_metric 判定）。
     *
     * <p>默认实现对四种类型都成立：{@code metric + 容差 >= target}。
     * 留成 default 是因为它<b>不该</b>被轻易改写 —— 若某天真需要不同判据，
     * 应先问「这是不是该落在 rule_config 里」。
     */
    default boolean isCompleted(BigDecimal metric, TaskRuleConfig rule) {
        return rule.reached(metric, rule.target());
    }
}
