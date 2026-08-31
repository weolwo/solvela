/*
 * 脚本管理
 *
 * 脚本的权威在 t_script：一行 = 一个版本，同一编码至多一行激活。
 * 本地 resources/scripts/ 只是开发工作区，运行期不读它。
 *
 * 🔴 保存与激活是两件事：save 只加一个版本，线上跑的还是原来那版；
 *    activate 才改变线上行为。发布与回滚都是 activate。
 *
 * @Date 2026-08-31
 */
import { getRequest, postRequest } from '/@/lib/axios';

export const scriptApi = {
  // 脚本列表，每个编码一行，取当前激活版本
  list: () => {
    return getRequest('/script/list');
  },

  // 单个版本详情，含内容。注意传的是版本行 id，不是 scriptCode —— 一个编码有多行
  detail: (id) => {
    return getRequest(`/script/detail?id=${id}`);
  },

  // 某个脚本的全部版本，新的在前，含内容
  versions: (scriptCode) => {
    return getRequest(`/script/versions?scriptCode=${encodeURIComponent(scriptCode)}`);
  },

  // 生成一个没被占用的脚本编码
  generateCode: () => {
    return getRequest('/script/generateCode');
  },

  // 保存一个新版本（语法不通过会被拒绝）。不改变线上行为
  save: (form) => {
    return postRequest('/script/save', form);
  },

  // 激活某个版本。发布与回滚都走它
  activate: (id) => {
    return postRequest(`/script/activate/${id}`);
  },

  // 改这个脚本会影响哪些业务对象
  refs: (scriptCode) => {
    return getRequest(`/script/refs?scriptCode=${encodeURIComponent(scriptCode)}`);
  },

  // 可挂载点清单，下拉用
  refPointList: () => {
    return getRequest('/script/ref/point/list');
  },

  // 某挂载点可选的业务对象。服务端按挂载点分发，前端不用认识各业务模块的 optionList
  refCandidateList: (refPoint) => {
    return getRequest(`/script/ref/candidate/list?refPoint=${encodeURIComponent(refPoint)}`);
  },

  // 挂载到业务对象
  bind: (form) => {
    return postRequest('/script/ref/bind', form);
  },

  // 摘除挂载。多值槽位要带 refKey，单值槽位不传
  unbind: (refPoint, refId, refKey) => {
    const query = refKey ? `?refKey=${encodeURIComponent(refKey)}` : '';
    return postRequest(`/script/ref/unbind/${refPoint}/${encodeURIComponent(refId)}${query}`);
  },

  // 场景契约（脚本能拿到哪些变量），编辑器与详情页都用
  sceneList: () => {
    return getRequest('/script/engine/scene/view');
  },

  // 语法校验，不执行
  check: (script) => {
    return postRequest('/script/engine/check', { script });
  },
};
