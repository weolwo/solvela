package sa.task.tasktemplate.domain.vo;

import java.util.Map;

/**
 * 任务模板选项VO：供任务配置向导第1步选模板、第2步按 uiSchema 渲染动态表单
 *
 * 与列表VO的区别：ui_schema 以 JSON 对象下发（列表VO里是原始字符串），前端拿到即可直接喂给 SchemaFormRenderer
 *
 * @param templateCode 模板编码（10位大写字母+数字）
 * @param templateName 模板名称
 * @param taskType     流转类型：SIMPLE / COUNT / AMOUNT
 * @param triggerEvent 默认触发事件（模板建议值，向导中可覆盖）
 * @param icon         卡片图标，取自 ui_schema.icon，缺省给一个兜底 emoji
 * @param description  卡片描述，取自 ui_schema.desc
 * @param uiSchema     前端渲染规则
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record TaskTemplateOptionVO(String templateCode,
                                   String templateName,
                                   String taskType,
                                   String triggerEvent,
                                   String icon,
                                   String description,
                                   Map<String, Object> uiSchema) {
}
