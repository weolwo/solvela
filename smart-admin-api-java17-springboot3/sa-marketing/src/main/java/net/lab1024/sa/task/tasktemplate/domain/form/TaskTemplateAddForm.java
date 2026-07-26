package net.lab1024.sa.task.tasktemplate.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import net.lab1024.sa.base.common.util.SmartCodeUtil;

/**
 * 任务模板表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */

@Data
public class TaskTemplateAddForm {

    @Schema(description = "租户ID，不传落库取默认值 '0'")
    private String tenantId;

    @Schema(description = "模板编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板编码 不能为空")
    @Pattern(regexp = SmartCodeUtil.BIZ_CODE_REGEX, message = "模板" + SmartCodeUtil.BIZ_CODE_MESSAGE)
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

    @Schema(description = "QLExpress脚本", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "QLExpress脚本 不能为空")
    private String ruleScript;

}