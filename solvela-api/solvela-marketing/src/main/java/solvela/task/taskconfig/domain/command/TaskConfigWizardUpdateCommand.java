package solvela.task.taskconfig.domain.command;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务配置向导 更新表单 —— 在提交表单基础上多一个 id。
 *
 * <p>⚠️ 表单里的 activityCode 会被服务端<b>无条件忽略</b>，一律以库里的为准：
 * 换活动意味着奖励阶梯里的 prize_code（按活动隔离）全部失效，换完必然发不出奖，
 * 那是「重建一个任务」而不是「改一个任务」。前端把这个下拉置灰了 —— 但服务端不能只靠前端置灰。
 *
 * <p>模板允许更换：切模板后 rule_config 会按新模板的 ui_schema 重新填写与校验。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskConfigWizardUpdateCommand extends TaskConfigWizardSubmitCommand {

    /** 任务配置ID */
    private Long id;
}
