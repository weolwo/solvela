package solvela.admin.module.scriptengine.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存一个脚本版本。
 *
 * <p>保存<b>不改变线上行为</b> —— 它只是往 {@code t_script} 加一行，
 * 要生效得再调一次激活接口。
 */
@Data
public class ScriptSaveForm {

    @Schema(description = "脚本编码，如 activity/mid_autumn_play。已存在则作为它的新版本",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "脚本编码不能为空")
    @Size(max = 64, message = "脚本编码最长 64 个字符")
    // 限定字符集不是洁癖：编码会出现在日志、报错信息与 URL 里，
    // 允许空格和中文会让「按编码搜日志」变得不可靠
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$", message = "脚本编码只能用字母、数字、下划线、中划线和斜杠")
    private String scriptCode;

    @Schema(description = "脚本名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "脚本名称不能为空")
    @Size(max = 128, message = "脚本名称最长 128 个字符")
    private String scriptName;

    @Schema(description = "场景枚举名，合法值见 /script/engine/scene/view。同一编码下不允许改",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "场景不能为空")
    private String scene;

    @Schema(description = "用途说明")
    @Size(max = 500, message = "用途说明最长 500 个字符")
    private String description;

    @Schema(description = "脚本内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "脚本内容不能为空")
    private String content;

    @Schema(description = "这一版改了什么")
    @Size(max = 255, message = "改动说明最长 255 个字符")
    private String changeLog;
}
