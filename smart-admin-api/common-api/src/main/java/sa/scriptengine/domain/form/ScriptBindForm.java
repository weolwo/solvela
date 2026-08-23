package sa.scriptengine.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 把脚本挂到某个挂载点上
 */
@Data
public class ScriptBindForm {

    @Schema(description = "挂载点枚举名，如 PRIZE_POOL_ENTRY", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "挂载点不能为空")
    private String refPoint;

    @Schema(description = "业务对象编码（pool_code / template_code / activity_code）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务对象编码不能为空")
    private String refId;

    @Schema(description = "脚本编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "脚本编码不能为空")
    private String scriptCode;
}
