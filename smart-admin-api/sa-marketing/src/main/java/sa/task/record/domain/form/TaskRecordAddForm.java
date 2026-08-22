package sa.task.record.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
public class TaskRecordAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "租户ID，不传落库取默认值 '0'")
    private String tenantId;

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号 不能为空")
    private Long memberId;

    @Schema(description = "任务配置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务配置ID 不能为空")
    private Long taskConfigId;

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "业务期数标识(防重用)：NONE, 日期(20260402)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务期数标识(防重用)：NONE, 日期(20260402) 不能为空")
    private String periodKey;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间 不能为空")
    private LocalDateTime validStartTime;

    @Schema(description = "过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "过期时间 不能为空")
    private LocalDateTime validEndTime;

    @Schema(description = "当前进度值：如已签到 3.0000 天", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "当前进度值：如已签到 3.0000 天 不能为空")
    private BigDecimal currentMetric;

    @Schema(description = "进度详情")
    private String progressData;

    @Schema(description = "接取任务时的规则快照")
    private String ruleSnapshot;

    @Schema(description = "接取任务时的奖励快照")
    private String prizeSnapshot;

}