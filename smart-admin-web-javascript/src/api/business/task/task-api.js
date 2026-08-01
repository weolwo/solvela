/**
 * 任务向导/模板设计器 api 封装
 *
 * @Author:    alaric
 * @Date:      2026-07-19
 */
import { getRequest, postRequest } from '/src/lib/axios';

export const taskApi = {
  /**
   * 任务向导用的模板列表（ui_schema 以 JSON 对象下发，可直接喂 SchemaFormRenderer）  @author  alaric
   */
  queryTemplateOptionList: () => {
    return getRequest('/taskTemplate/optionList');
  },

  /**
   * 生成模板编码（10位大写字母+数字，服务端已判重）  @author  alaric
   */
  generateTemplateCode: () => {
    return getRequest('/taskTemplate/generateCode');
  },

  /**
   * 触发事件下拉（只返回启用中的）。
   *
   * 事件本质是开放集合，此前写死在前端常量里，加一个事件要改前端 + 改 DDL 注释 + 可能改后端枚举，
   * 与「新增任务模板前端零改动」的目标正面冲突。现在改由 t_task_event 注册表下发：
   * 加事件 = 加一行数据 + 上游埋点，前端一行都不用动。
   *
   * 返回项含 bizIdRequired，用来提示配置的人「这个事件要求上游必须传业务单号」。
   * @author  alaric
   */
  queryEventOptionList: () => {
    return getRequest('/taskEvent/optionList');
  },

  /**
   * 提交任务配置向导（主子表事务：taskConfig + prizeMappingList，归属后端 taskconfig 模块）  @author  alaric
   */
  submitTaskConfig: (param) => {
    return postRequest('/taskConfig/wizard/submit', param);
  },

  /**
   * 保存任务模板设计器（uiSchema + ruleScript，归属后端 tasktemplate 模块）  @author  alaric
   */
  saveTaskTemplate: (param) => {
    return postRequest('/taskTemplate/save', param);
  },
};
