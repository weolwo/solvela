package solvela.admin.module.risk.promotiongroup.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 复制分组入参。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupCopyForm {

    @Schema(description = "要复制的源分组ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "源分组ID 不能为空")
    private Long id;

    @Schema(description = "新分组名称；留空则在原名后加「副本」")
    private String groupName;
}
