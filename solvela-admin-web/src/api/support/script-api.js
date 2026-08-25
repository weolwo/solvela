/*
 * 脚本管理
 *
 * 🔴 没有新增/编辑/删除脚本的接口，这是设计不是遗漏：
 *    脚本内容的权威在后端项目的 resources/scripts/ 下，改脚本必须走 git + 发版。
 *    本页只能「看」和「挂在哪」。
 *
 * @Date 2026-08-23
 */
import { getRequest, postRequest } from '/@/lib/axios';

export const scriptApi = {
  // 脚本列表（只读）
  list: () => {
    return getRequest('/script/list');
  },

  // 脚本详情，含内容
  detail: (scriptCode) => {
    return getRequest(`/script/detail?scriptCode=${encodeURIComponent(scriptCode)}`);
  },

  // 改这个脚本会影响哪些业务对象
  refs: (scriptCode) => {
    return getRequest(`/script/refs?scriptCode=${encodeURIComponent(scriptCode)}`);
  },

  // 可挂载点清单，下拉用
  refPointList: () => {
    return getRequest('/script/ref/point/list');
  },

  // 挂载到业务对象
  bind: (form) => {
    return postRequest('/script/ref/bind', form);
  },

  // 摘除挂载
  unbind: (refPoint, refId) => {
    return postRequest(`/script/ref/unbind/${refPoint}/${encodeURIComponent(refId)}`);
  },

  // 场景契约（脚本能拿到哪些变量），详情页展示用
  sceneList: () => {
    return getRequest('/script/engine/scene/view');
  },
};
