package net.lab1024.sa.ledger.coupon.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会员优惠券 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */

@Data
public class MemberCouponUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "券类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "券类型 不能为空")
    private String couponType;

    @Schema(description = "券名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "券名称 不能为空")
    private String couponName;

    @Schema(description = "状态：0-未使用, 1-已使用, 2-已过期, 3-已作废", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态：0-未使用, 1-已使用, 2-已过期, 3-已作废 不能为空")
    private Integer status;

    @Schema(description = "有效期开始", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "有效期开始 不能为空")
    private LocalDateTime validStartTime;

    @Schema(description = "有效期结束", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "有效期结束 不能为空")
    private LocalDateTime validEndTime;

    @Schema(description = "核销时间")
    private LocalDateTime usedTime;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}