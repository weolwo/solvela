package solvela.admin.module.task.tasktemplate.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

import java.util.Map;

/**
 * 任务模板设计器 保存表单（按 templateCode upsert）
 *
 * @Author alaric
 * @Date 2026-07-19
 */
@Data
public class TaskTemplateSaveForm {

    @Schema(description = "模板编码：10 位大写字母+数字，全局唯一，upsert 唯一键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板编码 不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "模板" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String templateCode;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称 不能为空")
    private String templateName;

    @Schema(description = "流转类型：SIMPLE, COUNT, AMOUNT", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务类型 不能为空")
    private String taskType;

    @Schema(description = "默认触发事件：模板建议值，向导中可覆盖")
    private String triggerEvent;

    @Schema(description = "前端渲染规则 ui_schema（JSON 对象）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "ui_schema 不能为空")
    private Map<String, Object> uiSchema;

}
