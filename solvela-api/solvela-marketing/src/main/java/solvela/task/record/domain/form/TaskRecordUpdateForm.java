package solvela.task.record.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
public class TaskRecordUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "业务期数标识(防重用)：NONE, 日期(20260402)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务期数标识(防重用)：NONE, 日期(20260402) 不能为空")
    private String periodKey;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间 不能为空")
    private LocalDateTime validStartTime;

    @Schema(description = "过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "过期时间 不能为空")
    private LocalDateTime validEndTime;

    @Schema(description = "状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期")
    private Integer status;

    @Schema(description = "进度详情")
    private String progressData;

    @Schema(description = "接取任务时的规则快照")
    private String ruleSnapshot;

    @Schema(description = "接取任务时的奖励快照")
    private String prizeSnapshot;

    @Schema(description = "达标时间")
    private LocalDateTime completeTime;

}