package solvela.admin.module.activity.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 复制活动入参。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class ActivityConfigCopyForm {

    @Schema(description = "要复制的源活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "源活动编码 不能为空")
    private String activityCode;

    @Schema(description = "新活动名称；留空则在原名后加「副本」")
    private String activityName;
}
