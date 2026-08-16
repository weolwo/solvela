package sa.draw.runtime.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 执行抽奖 表单
 * memberName 联调期显式传入；C端接入后改为从登录态获取并删除该字段
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawExecuteForm {

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "奖池编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池编码 不能为空")
    private String poolCode;

    @Schema(description = "会员名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员名 不能为空")
    private String memberName;

    @Schema(description = "请求ID：传入则启用幂等防重（网络重试不会重复抽奖）")
    private String requestId;
}
