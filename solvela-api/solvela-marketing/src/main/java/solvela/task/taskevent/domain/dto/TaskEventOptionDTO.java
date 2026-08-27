package solvela.task.taskevent.domain.dto;

/**
 * 任务事件下拉项（向导第 1 步用）。
 *
 * <p>用 record：它是只读投影，没有任何理由可变。
 *
 * <p>{@code payloadSchema} 以<b>已解析的对象</b>下发（不是原始字符串），
 * 前端拿到即可直接渲染字段提示 —— 与 {@code TaskTemplateOptionDTO} 对 ui_schema 的处理同一口径。
 *
 * @param bizIdRequired 前端据此在选中该事件时提示「上游必须带单号」，
 *                      让配置的人在配的时候就知道对接方要传什么
 * @Author alaric
 * @Date 2026-08-01
 */
public record TaskEventOptionDTO(
        String eventCode,
        String eventName,
        String metricSource,
        Boolean bizIdRequired,
        Object payloadSchema) {
}
