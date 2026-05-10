package net.lab1024.sa.ledger.logistic.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 发货物流表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */

@Data
public class PhysicalDeliveryUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

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

    @Schema(description = "状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回")
    private Integer status;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}