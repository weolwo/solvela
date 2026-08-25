package solvela.admin.module.scriptengine.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 在线校验 / 试跑入参
 */
@Data
public class ScriptTestForm {

    @Schema(description = "脚本内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "脚本内容不能为空")
    private String script;

    @Schema(description = "上下文变量，key 即脚本里直接引用的变量名")
    private Map<String, Object> variables;
}
