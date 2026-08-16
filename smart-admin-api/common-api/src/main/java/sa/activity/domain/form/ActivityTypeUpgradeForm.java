package sa.activity.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import sa.base.common.validator.enumeration.CheckEnum;
import sa.enums.ActivityTypeEnum;

/**
 * 活动类型升级表单：仅 BASIC → DRAW/TASK/LOTTERY。
 *
 * 为什么单独开一个窄接口，而不是把 activityType 放回 UpdateForm：
 * 普通编辑绝不该碰类型（改一次类型，已配的玩法配置全部变成查不到也删不掉的孤儿数据），
 * 所以 UpdateForm 里刻意不定义该字段、让越权意图在编译期无处安放。
 * 而 BASIC → 玩法类的升级是合法的 —— BASIC 按定义没有玩法下游，升级不产生任何孤儿数据。
 * 给一个专用出口，好过把大门敞开。
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Data
public class ActivityTypeUpgradeForm {

    @Schema(description = "活动id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动id 不能为空")
    private Long id;

    @Schema(description = "升级到的玩法类型：DRAW / TASK / LOTTERY（不接受 BASIC）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @CheckEnum(value = ActivityTypeEnum.class, required = true, message = "目标活动类型非法")
    private String targetType;
}
