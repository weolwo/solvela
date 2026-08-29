package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务流水类型，对齐 {@code t_task_record_flow.flow_type}。
 *
 * <p>同一张流水表同时记「生效了的」和「被丢弃的」两类事件。
 * 丢弃也要落库是刻意的：任务没涨进度时，运营的第一个问题永远是「事件到底有没有来」，
 * 没有丢弃流水就只能靠翻日志猜。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum TaskFlowTypeEnum implements BaseEnum {

    ADVANCE(1, "进度推进"),

    /**
     * 事件丢弃：未生效。{@code discard_reason} 必填
     */
    DISCARD(2, "事件丢弃"),
    ;

    private final Integer value;

    private final String desc;
}
