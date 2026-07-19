/**
 * 任务向导/模板设计器 api 封装
 *
 * @Author:    alaric
 * @Date:      2026-07-19
 */
import { postRequest } from '/src/lib/axios';

export const taskApi = {
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
