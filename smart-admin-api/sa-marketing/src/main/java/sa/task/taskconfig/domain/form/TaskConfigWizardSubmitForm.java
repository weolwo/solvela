package sa.task.taskconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 任务配置向导 提交表单（主子表：t_task_config + t_task_prize_mapping，一个事务内落库）
 * 结构与前端契约一致：{ taskConfig: {...}, prizeMappingList: [...] }
 *
 * @Author alaric
 * @Date 2026-07-19
 */
@Data
public class TaskConfigWizardSubmitForm {

    @Schema(description = "主表配置", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "taskConfig 不能为空")
    @Valid
    private TaskConfigWizardConfigForm taskConfig;

    @Schema(description = "奖励阶梯列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "奖励阶梯 至少配置一级")
    @Valid
    private List<TaskConfigWizardPrizeItemForm> prizeMappingList;
}
