package net.lab1024.sa.activity.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import lombok.Data;
import net.lab1024.sa.base.common.util.SmartCodeUtil;

/**
 * 活动配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */

@Data
public class ActivityConfigAddForm {

    @Schema(description = "租户id，不传落库取默认值 '0'")
    private String tenantId;

    @Schema(description = "活动编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    @Pattern(regexp = SmartCodeUtil.BIZ_CODE_REGEX, message = "活动" + SmartCodeUtil.BIZ_CODE_MESSAGE)
    private String activityCode;

    @Schema(description = "活动名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动名称 不能为空")
    private String activityName;

    @Schema(description = "活动类型")
    private String activityType;

    @Schema(description = "状态：0-未开始, 1-上线, 2-下线")
    private Integer status;

    @Schema(description = "活动开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动开始时间 不能为空")
    private LocalDateTime startTime;

    @Schema(description = "活动结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动结束时间 不能为空")
    private LocalDateTime endTime;

    @Schema(description = "规则脚本id")
    private String scriptId;

}