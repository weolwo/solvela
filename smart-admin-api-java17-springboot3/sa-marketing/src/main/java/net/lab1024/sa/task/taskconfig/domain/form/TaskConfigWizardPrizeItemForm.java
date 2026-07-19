package net.lab1024.sa.task.taskconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务配置向导 奖励阶梯子表项（t_task_prize_mapping）
 *
 * @Author alaric
 * @Date 2026-07-19
 */
@Data
public class TaskConfigWizardPrizeItemForm {

    @Schema(description = "阶梯层级，从1开始", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "阶梯层级 不能为空")
    private Integer stageLevel;

    @Schema(description = "达标条件数值：COUNT型为次数，AMOUNT型为金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "达标条件 不能为空")
    private Integer stageCondition;

    @Schema(description = "奖励编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖励编码 不能为空")
    private String prizeCode;

    @Schema(description = "计算类型：FIXED(固定), RATIO(比例), FORMULA(公式)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "计算类型 不能为空")
    private String prizeMode;

    @Schema(description = "奖励额度", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "奖励额度 不能为空")
    private BigDecimal prizeValue;
}
