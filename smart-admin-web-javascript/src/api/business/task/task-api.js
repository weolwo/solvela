/**
 * 任务向导/模板设计器 api 封装
 *
 * @Author:    alaric
 * @Date:      2026-07-19
 */
import { postRequest } from '/src/lib/axios';

export const taskApi = {
  /**
   * 提交任务配置向导（主子表：taskConfig + prizeMappingList）  @author  alaric
   */
  submitTaskConfig: (param) => {
    return postRequest('/task/config/submit', param);
  },

  /**
   * 保存任务模板设计器（uiSchema + ruleScript）  @author  alaric
   */
  saveTaskTemplate: (param) => {
    return postRequest('/task/template/save', param);
  },
};
