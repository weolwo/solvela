package solvela.task.tasktemplate.domain.command;

import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

/**
 * 任务模板表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */

@Data
public class TaskTemplateAddCommand {

    /** 模板编码：10 位大写字母+数字，全局唯一 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型) */
    private String taskType;

    /** 默认触发事件：模板建议值，向导中可覆盖 */
    private String triggerEvent;

    /** 前端渲染规则 */
    private String uiSchema;

}