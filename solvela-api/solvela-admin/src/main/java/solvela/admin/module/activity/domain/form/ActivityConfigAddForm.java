package solvela.admin.module.activity.domain.form;

import solvela.enums.ActivityStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.ActivityTypeEnum;

/**
 * 活动配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */

@Data
public class ActivityConfigAddForm {

    @Schema(description = "活动编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "活动" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String activityCode;

    @Schema(description = "活动名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动名称 不能为空")
    private String activityName;

    @Schema(description = "活动类型：BASIC-基础活动 / DRAW-奖池抽奖 / TASK-任务驱动 / LOTTERY-FPE彩票",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @CheckEnum(value = ActivityTypeEnum.class, required = true, message = "活动类型非法")
    private String activityType;

    @Schema(description = "状态：0-未开始, 1-上线, 2-下线")
    private ActivityStatusEnum status;

    @Schema(description = "活动开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动开始时间 不能为空")
    private LocalDateTime startTime;

    @Schema(description = "活动结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动结束时间 不能为空")
    private LocalDateTime endTime;

    @Schema(description = "数据截止时间：此刻起不再受理参与（抽奖/任务累计），但活动仍可见、已中的奖仍可领到活动结束时间。不填表示与活动结束时间相同")
    private LocalDateTime dataEndTime;

}