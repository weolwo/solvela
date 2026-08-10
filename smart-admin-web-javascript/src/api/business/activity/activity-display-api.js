/*
 * 活动 C 端展示配置
 *
 * 对外一律用 activityCode 寻址：整个活动域都是这么做的，而 wizardCreate 压根不返回 id。
 *
 * @Copyright  1024创新实验室 （ https://1024lab.net ）
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const activityDisplayApi = {
  /**
   * 查询展示配置。没配过时返回 null，前端按空表单处理
   */
  get: (activityCode) => {
    return getRequest(`/activityDisplay/get/${activityCode}`);
  },

  /**
   * 保存。新增还是更新由后端按 activityId 判断，前端不用区分。
   * 保存的同时后端会把引用到的图片登记进 t_file_relation
   */
  save: (activityCode, param) => {
    return postRequest(`/activityDisplay/save/${activityCode}`, param);
  },
};
