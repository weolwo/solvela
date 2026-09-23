/**
 * 等级权益发放配置 api 封装
 *
 * 🔴 这里配的东西【会真的花钱】。与「等级权益（展示）」不是一回事：
 *    那个改的是页面上写什么，这个改的是真的发什么出去——
 *    加一条「每月给所有白金发 20 元券」，下一个 job 周期就真的发了。
 *    所以权限点是单独的 memberEntitlement:config，不跟 memberGrade:config 共用。
 *
 * 🔴 没有 delete：已经生成的待领取记录是【已经承诺给用户的东西】，
 *    删配置会让它们变成查不到来源的孤儿。要停就停用——停用不影响已生成的，
 *    只是不再生成新的。
 *
 * @Author:    alaric
 * @Date:      2026-09-22
 */
import { getRequest, postRequest } from '/@/lib/axios';

export const memberEntitlementApi = {
  /** 全部权益配置（含停用）  @author alaric */
  list: () => {
    return getRequest('/memberEntitlement/list');
  },

  /**
   * 新增 / 编辑。
   * ⚠️ 编辑时【类型不可改】——服务端会忽略传来的类型，沿用原值。
   *    改类型等于改幂等键的口径（yyyy ↔ yyyyMM），会让同一个周期再发一次。
   * @author alaric
   */
  save: (param) => {
    return postRequest('/memberEntitlement/save', param);
  },

  /** 启用 / 停用。不影响已生成的待领取  @author alaric */
  updateStatus: (id, status) => {
    return getRequest(`/memberEntitlement/updateStatus?id=${id}&status=${status}`);
  },
};
