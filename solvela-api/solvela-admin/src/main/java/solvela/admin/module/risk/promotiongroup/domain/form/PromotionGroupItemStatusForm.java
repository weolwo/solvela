package solvela.admin.module.risk.promotiongroup.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

/**
 * 组内单条配置的发放开关入参。
 *
 * <p>与分组主开关（{@code PromotionGroupStatusForm}）不同，这里不需要类型名单 ——
 * 它只管一条配置，开哪一条是调用方点的那一行说了算。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupItemStatusForm {

    @Schema(description = "优惠配置ID（不是分组ID）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "优惠配置ID 不能为空")
    private Long id;

    @Schema(description = "目标状态：0-停用, 1-启用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态 不能为空")
    private EnableStatusEnum status;
}
