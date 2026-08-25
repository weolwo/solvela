package solvela.task.runtime.domain;

import java.math.BigDecimal;

/**
 * 单个任务配置的推进结果。
 *
 * <p>sealed + record，消费端用模式匹配穷尽 —— 新增一种结果时编译器会逼所有消费点都处理，
 * 而不是悄悄落进某个 default 分支。
 *
 * @author alaric
 * @date 2026-08-01
 */
public sealed interface TaskAdvanceResult {

    /**
     * 进度已推进
     *
     * @param completed 本次是否让任务达标（最高档）
     */
    record Advanced(Long recordId, BigDecimal before, BigDecimal after, boolean completed) implements TaskAdvanceResult {
    }

    /**
     * 幂等命中：该事件对该任务配置已处理过，本次什么都不做。
     *
     * <p>这<b>不是失败</b> —— 上游重投、MQ 重复消费都是正常现象，
     * 幂等生效恰恰说明防重在工作，不该按错误上报告警。
     */
    record Duplicated(String eventBizId) implements TaskAdvanceResult {
    }

    /**
     * 事件被丢弃：条件不满足。reason 已落进 t_task_record_flow.discard_reason
     */
    record Discarded(String reason) implements TaskAdvanceResult {
    }
}
