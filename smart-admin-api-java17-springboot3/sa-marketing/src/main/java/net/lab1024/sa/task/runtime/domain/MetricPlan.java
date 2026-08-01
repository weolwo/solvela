package net.lab1024.sa.task.runtime.domain;

import net.lab1024.sa.task.constant.TaskDiscardCode;

import java.math.BigDecimal;

/**
 * 进度推进方案：策略只负责「算出该怎么推」，SQL 由 TaskRecordAdvanceService 统一执行。
 *
 * <p>这样分工有三个好处：
 * ① 策略可纯函数单测，不需要数据库；
 * ② 累加型与读-改-写型的差别<b>在类型上就是显式的</b>，不会有人不小心把 COUNT 写成读-改-写；
 * ③ 更新 SQL 只有一处，并发语义不会各处漂移。
 *
 * <p>用 sealed + record，与 draw 模块的 {@code DrawResult} 同一写法，消费端用模式匹配穷尽。
 *
 * @author alaric
 * @date 2026-08-01
 */
public sealed interface MetricPlan {

    /**
     * 条件更新原子累加：{@code SET current_metric = current_metric + delta}。
     *
     * <p>一条 SQL 完成，靠行锁天然串行 —— 无 Lost Update、无需版本号、无需重试。
     * COUNT / AMOUNT / SIMPLE 都走这条。
     */
    record Accumulate(BigDecimal delta) implements MetricPlan {
    }

    /**
     * 乐观锁覆写：{@code SET current_metric = ?, progress_data = ? WHERE version = ?}。
     *
     * <p>只有 STREAK 用 —— 断档要清零，必须先读 lastHitDate 才知道算多少，读-改-写无法避免。
     * 冲突时由上层有限次重试。
     *
     * @param expectedVersion 读到的版本号，作为并发闸门
     */
    record Overwrite(BigDecimal metric, String progressData, Integer expectedVersion) implements MetricPlan {
    }

    /**
     * 不推进：条件不满足（金额不够、同日重复、任务已完成…）。
     *
     * <p><b>两个字段都要给，它们的读者不同</b>：
     * <ul>
     *   <li>{@code reason} 给人读，落 {@code discard_reason} ——
     *       「用户下了 99 元的单为什么没进度」这类客诉的答案，<b>要写人话、要带具体数值</b>；</li>
     *   <li>{@code code} 给机器读，落 {@code discard_code} ——
     *       大屏按它聚类，取值封闭，改文案不会让统计图裂开。</li>
     * </ul>
     *
     * <p>做成 record 的两个必填分量而不是「code 可选」，是为了让策略实现<b>无法忘记</b>分类 ——
     * 忘了就编译不过，而不是等到大屏上多出一堆归不了类的丢弃。
     */
    record Skip(TaskDiscardCode code, String reason) implements MetricPlan {
    }
}
