/**
 * 系统级-任务模板表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:07:43
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const taskTemplateApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/taskTemplate/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/taskTemplate/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/taskTemplate/update', param);
  },

  /**
   * 启用/禁用：单个开关与批量禁用共用，替代删除。
   * ⚠️ 模板被 t_task_config.template_code 引用，删掉会让引用它的任务安静地不再推进  @author  alaric
   */
  updateStatus: (param) => {
    return postRequest('/taskTemplate/updateStatus', param);
  },

  /**
   * 模板详情：供模板设计器编辑态回显 ui_schema / rule_script  @author  alaric
   */
  detail: (id) => {
    return getRequest(`/taskTemplate/detail/${id}`);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/taskTemplate/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/taskTemplate/batchDelete', idList);
  },
};
