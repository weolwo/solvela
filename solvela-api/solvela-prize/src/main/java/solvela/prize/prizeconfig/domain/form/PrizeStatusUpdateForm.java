package solvela.prize.prizeconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 奖品启用 / 禁用表单：单个开关与批量禁用共用一个接口。
 *
 * <p>不复用 {@link PrizeConfigUpdateForm}：那个表单的 approveMode 是 @NotNull，
 * 只想改一个 status 却要把审批模式一起回传，既冗余又给了顺手改错的机会。
 *
 * @Author weolwo
 * @Date 2026-07-30
 */
@Data
public class PrizeStatusUpdateForm {

    @Schema(description = "奖品id列表，单个操作也用列表传", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少选择一个奖品")
    private List<Long> idList;

    @Schema(description = "目标状态：1-启用, 0-禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态 不能为空")
    private Integer status;
}
