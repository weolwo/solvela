package solvela.task.tasktemplate.domain.command;

import lombok.Data;

/**
 * 任务模板表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */

@Data
public class TaskTemplateUpdateCommand {

    private Long id;

    /** 模板名称 */
    private String templateName;

    /** 流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型) */
    private String taskType;

    /** 默认触发事件：模板建议值，向导中可覆盖 */
    private String triggerEvent;

    /** 前端渲染规则 */
    private String uiSchema;

}