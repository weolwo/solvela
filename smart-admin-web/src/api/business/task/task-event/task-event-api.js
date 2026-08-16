/**
 * 任务事件注册表 api 封装
 *
 * 事件是开放集合：加一个事件 = 在这张表加一行 + 上游埋点，前端与后端都不用改代码。
 *
 * @Author:    alaric
 * @Date:      2026-08-01
 */
import { getRequest, postRequest } from '/src/lib/axios';

export const taskEventApi = {
  /**
   * 分页查询  @author  alaric
   */
  queryPage: (param) => {
    return postRequest('/taskEvent/queryPage', param);
  },

  /**
   * 注册新事件  @author  alaric
   */
  add: (param) => {
    return postRequest('/taskEvent/add', param);
  },

  /**
   * 更新（事件编码不可改，服务端 UpdateForm 里刻意不定义该字段）  @author  alaric
   */
  update: (param) => {
    return postRequest('/taskEvent/update', param);
  },

  /**
   * 删除（仍被任务配置引用时服务端会拒绝，并提示改用「停用」）  @author  alaric
   */
  delete: (id) => {
    return getRequest(`/taskEvent/delete/${id}`);
  },

  /**
   * 查询任务记录的事件流水（含被丢弃的事件与原因）—— 客诉自证入口  @author  alaric
   */
  queryRecordFlow: (recordId) => {
    return getRequest(`/taskEvent/flow/${recordId}`);
  },
};
