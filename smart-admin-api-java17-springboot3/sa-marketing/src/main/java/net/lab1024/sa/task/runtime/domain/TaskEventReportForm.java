package net.lab1024.sa.task.runtime.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 上游埋点上报任务事件。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Data
public class TaskEventReportForm {

    @Schema(description = "事件编码：DAILY_SIGN / ORDER_PAID / GOODS_SHARE ...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "事件编码不能为空")
    private String eventCode;

    @Schema(description = "会员名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员名不能为空")
    private String memberName;

    /**
     * 幂等键。
     *
     * <p>⚠️ 有天然单号的事件（ORDER_PAID 的订单号）<b>必须传</b>，否则同一笔订单会被累计多次 ——
     * 服务端只能按「事件日」兜底，那对订单类事件意味着「一天只算一笔」，同样是错的。
     *
     * <p>P0 阶段先靠约定；P1 的 {@code t_task_event.biz_id_required} 会把它变成表里的强制契约，
     * 缺失直接拒绝并落丢弃流水（方案 §2.2）。
     */
    @Schema(description = "上游幂等单号（订单号等）。无天然单号的事件可不传，服务端按事件日兜底")
    private String eventBizId;

    @Schema(description = "计量值，AMOUNT 类任务用（如订单实付金额）")
    private BigDecimal amount;

    @Schema(description = "事件实际发生时间。不传则取数据库时钟；迟到的事件应传真实发生时间，否则会归错周期")
    private LocalDateTime eventTime;

    @Schema(description = "事件原文，落进流水供客诉复盘")
    private Map<String, Object> payload;
}
