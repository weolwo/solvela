package solvela.task.tasktemplate.domain.command;

import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

import java.util.Map;

/**
 * 任务模板设计器 保存表单（按 templateCode upsert）
 *
 * @Author alaric
 * @Date 2026-07-19
 */
@Data
public class TaskTemplateSaveCommand {

    /** 模板编码：10 位大写字母+数字，全局唯一，upsert 唯一键 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 流转类型：SIMPLE, COUNT, AMOUNT */
    private String taskType;

    /** 默认触发事件：模板建议值，向导中可覆盖 */
    private String triggerEvent;

    /** 前端渲染规则 ui_schema（JSON 对象） */
    private Map<String, Object> uiSchema;

}
