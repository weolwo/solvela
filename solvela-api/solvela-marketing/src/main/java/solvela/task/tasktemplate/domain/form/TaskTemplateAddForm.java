package solvela.task.tasktemplate.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import solvela.base.common.util.SolvelaCodeUtil;

/**
 * 任务模板表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */

@Data
public class TaskTemplateAddForm {

    @Schema(description = "模板编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板编码 不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "模板" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String templateCode;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称 不能为空")
    private String templateName;

    @Schema(description = "流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型) 不能为空")
    private String taskType;

    @Schema(description = "默认触发事件：模板建议值，向导中可覆盖")
    private String triggerEvent;

    @Schema(description = "前端渲染规则", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "前端渲染规则 不能为空")
    private String uiSchema;

}