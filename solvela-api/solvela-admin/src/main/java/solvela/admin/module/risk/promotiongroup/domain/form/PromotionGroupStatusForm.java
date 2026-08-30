package solvela.admin.module.risk.promotiongroup.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.util.List;

/**
 * 分组主开关的入参。
 *
 * <p>为什么开启要多带一个名单：关掉分组的那一刻，「原来哪几种类型是开的」
 * 这个信息就被覆盖掉了。再开时猜一个默认（比如全开）的代价是
 * 把本来就该停发的类型重新放出去 —— 那是静默的资损方向，
 * 所以让人当场确认一次。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupStatusForm {

    @Schema(description = "分组ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分组ID 不能为空")
    private Long id;

    @Schema(description = "目标状态：0-停用, 1-启用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态 不能为空")
    private EnableStatusEnum status;

    @Schema(description = "要启用的资产类型；仅 status=1 时有意义，组内有配置时不能为空")
    private List<String> enablePrizeTypes;
}
