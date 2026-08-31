/**
 * 抽奖配置 api 封装
 *
 * 抽奖玩法这一层的配置，与彩票配置同层。它补的是抽奖原本缺的一层：
 * 活动 → 抽奖配置 → N 个奖池。
 *
 * 重置周期、抽奖算法都住在这里（原本挂在奖池上，是挂错了层），
 * 脚本挂载点 DRAW_PLAY 引用的也是这里的 drawCode。
 *
 * @Date 2026-08-31
 */
import { getRequest, postRequest } from '/@/lib/axios';

export const drawConfigApi = {
  // 列表
  list: () => {
    return getRequest('/drawConfig/list');
  },

  // 按活动查。活动没有抽奖配置时返回 null
  detailByActivity: (activityCode) => {
    return getRequest(`/drawConfig/detail/byActivity/${encodeURIComponent(activityCode)}`);
  },

  // 生成一个没被占用的抽奖编码
  generateCode: () => {
    return getRequest('/drawConfig/generateCode');
  },

  // 新增。一个活动只能有一套，重复会被拒
  add: (param) => {
    return postRequest('/drawConfig/add', param);
  },

  // 修改。drawCode 与 activityCode 服务端会忽略——前者是脚本挂载的引用键
  update: (param) => {
    return postRequest('/drawConfig/update', param);
  },

  // 删除。底下还有奖池时会被拒绝
  delete: (id) => {
    return getRequest(`/drawConfig/delete/${id}`);
  },
};
