package net.lab1024.sa.ledger.logistic.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 发货物流表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */

@Data
public class PhysicalDeliveryAddForm {

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "会员名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员名 不能为空")
    private String memberName;

    @Schema(description = "发奖提案ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "发奖提案ID 不能为空")
    private Long proposalId;

    @Schema(description = "来源类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源类型 不能为空")
    private String sourceType;

    @Schema(description = "收件人姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人姓名 不能为空")
    private String receiverName;

    @Schema(description = "收件人电话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人电话 不能为空")
    private String receiverPhone;

    @Schema(description = "收件详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件详细地址 不能为空")
    private String receiverAddress;

    @Schema(description = "物流公司")
    private String logisticsCompany;

    @Schema(description = "物流单号")
    private String logisticsNo;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}