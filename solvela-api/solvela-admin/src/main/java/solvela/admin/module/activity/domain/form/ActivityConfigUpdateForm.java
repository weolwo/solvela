package solvela.admin.module.activity.domain.form;

import solvela.enums.ActivityStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 活动配置 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */

@Data
public class ActivityConfigUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "活动名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动名称 不能为空")
    private String activityName;

    /*
     * ⚠️ 刻意不定义 activityType —— 活动类型创建后不可通过普通编辑修改。
     *
     * 类型决定了下游整块配置挂在哪套表上：一个 DRAW 活动下已配的奖池、物资、坑位映射
     * 全部以 activity_code 关联，改成 LOTTERY 后这些行仍在库里，
     * 但 LotteryWorkbench 查不到、DrawWorkbench 也不再列出该活动（它按 activityType 过滤下拉）
     * —— 数据既删不掉也看不见。
     *
     * 不定义字段，让越权意图在编译期就无处安放（同 LotteryIssueUpdateForm 对 issueNo 的处理）。
     * 唯一合法的类型变更是 BASIC → 玩法类的升级，走独立窄接口 /activityConfig/upgradeType，
     * 服务端会校验「当前必须是 BASIC」+「下游玩法表为空」两条。
     */

    @Schema(description = "状态：0-未开始, 1-上线, 2-下线")
    private ActivityStatusEnum status;

    @Schema(description = "活动开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动开始时间 不能为空")
    private LocalDateTime startTime;

    @Schema(description = "活动结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动结束时间 不能为空")
    private LocalDateTime endTime;

}