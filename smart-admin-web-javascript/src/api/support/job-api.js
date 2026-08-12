/*
 * job api
 *
 * @Author:    huke
 * @Date:      2024/06/25
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const jobApi = {
  // 分页查询 @huke
  queryJob: (param) => {
    return postRequest('/support/job/query', param);
  },
  // 定时任务-查询详情 @huke
  queryJobInfo: (param) => {
    return getRequest(`/support/job/${param}`);
  },
  // 执行任务 @huke
  executeJob: (param) => {
    return postRequest('/support/job/execute', param);
  },
  // 定时任务-新增-任务信息 @huke
  addJob: (param) => {
    return postRequest('/support/job/add', param);
  },
  // 定时任务-更新-任务信息 @huke
  updateJob: (param) => {
    return postRequest('/support/job/update', param);
  },
  // 定时任务-更新-开启状态 @huke
  updateJobEnabled: (param) => {
    return postRequest('/support/job/update/enabled', param);
  },
  // 定时任务-执行记录-分页查询 @huke
  queryJobLog: (param) => {
    return postRequest('/support/job/log/query', param);
  },
  // 定时任务-删除  @zhuoda
  deleteJob: (param) => {
    return getRequest(`/support/job/delete?jobId=${param}`);
  },
  // 定时任务-查询所有已注册的执行器（新增任务时下拉选择） @alaric
  queryHandlerList: () => {
    return getRequest('/support/job/handler/list');
  },
  // 定时任务-一键重跑（沿用原参数与业务日期，不是拿当前配置跑） @alaric
  rerunJobLog: (logId) => {
    return getRequest(`/support/job/log/rerun?logId=${logId}`);
  },
  // 定时任务-终止执行（发出中断信号，能否停止取决于执行器是否响应中断） @alaric
  terminateJobLog: (logId) => {
    return getRequest(`/support/job/log/terminate?logId=${logId}`);
  },
  // 定时任务-查看某次执行的实时日志 @alaric
  queryExecuteLog: (logId) => {
    return getRequest(`/support/job/log/detail?logId=${logId}`);
  },
  // 定时任务-触发时间预览（保存前算出接下来 5 次） @alaric
  previewTriggerTime: (param) => {
    return postRequest('/support/job/trigger/preview', param);
  },
};
