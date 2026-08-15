/**
 * 运行时-会员任务进度实例表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:03:33
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const taskRecordApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/taskRecord/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/taskRecord/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/taskRecord/update', param);
  },

  /**
   * 批量禁用：置为 3-已过期。
   * ⚠️ t_task_record.status 没有「禁用」这一档，「不再推进、不再发奖」的终态只有已过期，
   * 服务端也只放行 3  @author  alaric
   */
  updateStatus: (param) => {
    return postRequest('/taskRecord/updateStatus', param);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/taskRecord/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/taskRecord/batchDelete', idList);
  },
};
