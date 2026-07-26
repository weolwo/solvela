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
