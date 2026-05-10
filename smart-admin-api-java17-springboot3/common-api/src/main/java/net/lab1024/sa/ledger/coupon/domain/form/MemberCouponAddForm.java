package net.lab1024.sa.ledger.coupon.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会员优惠券 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */

@Data
public class MemberCouponAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "会员名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员名 不能为空")
    private String memberName;

    @Schema(description = "券模编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "券模编码 不能为空")
    private String couponCode;

    @Schema(description = "券类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "券类型 不能为空")
    private String couponType;

    @Schema(description = "券名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "券名称 不能为空")
    private String couponName;

    @Schema(description = "状态：0-未使用, 1-已使用, 2-已过期, 3-已作废", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态：0-未使用, 1-已使用, 2-已过期, 3-已作废 不能为空")
    private Integer status;

    @Schema(description = "来源：DRAW, TASK, MANUAL_SEND", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源：DRAW, TASK, MANUAL_SEND 不能为空")
    private String sourceType;

    @Schema(description = "关联单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "关联单号 不能为空")
    private String sourceBizId;

    @Schema(description = "有效期开始", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "有效期开始 不能为空")
    private LocalDateTime validStartTime;

    @Schema(description = "有效期结束", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "有效期结束 不能为空")
    private LocalDateTime validEndTime;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}