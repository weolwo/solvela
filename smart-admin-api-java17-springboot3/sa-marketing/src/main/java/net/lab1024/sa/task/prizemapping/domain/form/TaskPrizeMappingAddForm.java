package net.lab1024.sa.task.prizemapping.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务阶段与奖励映射表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */

@Data
public class TaskPrizeMappingAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "任务配置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务配置ID 不能为空")
    private Long taskConfigId;

    @Schema(description = "阶段达标条件：如 {min: 10, max: 99} 或 {action: share}", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "阶段达标条件：如 {min: 10, max: 99} 或 {action: share} 不能为空")
    private String stageCondition;

    @Schema(description = "任务阶段：单次任务填1，阶梯任务填 1, 2, 3...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务阶段：单次任务填1，阶梯任务填 1, 2, 3... 不能为空")
    private Integer stageLevel;

    @Schema(description = "奖励编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖励编码 不能为空")
    private String prizeCode;

    @Schema(description = "计算类型：FIXED(固定), RATIO(比例), FORMULA(公式)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "计算类型：FIXED(固定), RATIO(比例), FORMULA(公式) 不能为空")
    private String prizeMode;

    @Schema(description = "动态发奖策略JSON：如 {amount: 20} 或 {ratio: 0.05}")
    private String prizeStrategy;

}