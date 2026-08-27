package solvela.task.taskconfig.domain.command;

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
public class TaskConfigWizardSubmitCommand {

    /** 主表配置 */
    private TaskConfigWizardConfigCommand taskConfig;

    /** 奖励阶梯列表 */
    private List<TaskConfigWizardPrizeItemCommand> prizeMappingList;
}
