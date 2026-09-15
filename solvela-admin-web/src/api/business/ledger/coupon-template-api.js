/**
 * 券模板 api 封装
 *
 * 🔴 **这里没有 update，也没有 delete。都不是漏了，服务端同样没有。**
 *
 * 和通知模板是同一个道理，但后果更重：通知模板改版只是措辞变了，
 * 券改版是**钱变了**。用户手里那张「满100减20」，运营把模板改成「满200减20」
 * 之后，如果核销读的是模板当前值，用户手里的券就贬值了。
 *
 * 所以发券时会把规则**快照**进 t_member_coupon，核销根本不读模板；
 * 而模板这边只能往前加版本。老版本也删不得 —— 删了就再也回答不了
 * 「用户手里这张券当时是什么规则」，而券的纠纷恰恰总是要回答这个。
 *
 * @Author:    alaric
 * @Date:      2026-09-15
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const couponTemplateApi = {
  /** 模板列表，每个券编码取最新启用版 */
  list: () => {
    return getRequest('/couponTemplate/list');
  },

  /** 某个券编码的全部历史版本，新的在前 */
  versions: (couponCode) => {
    return getRequest(`/couponTemplate/versions?couponCode=${encodeURIComponent(couponCode)}`);
  },

  /** 新增一版。版本号由服务端算「当前最大版本 + 1」，前端不要传 */
  save: (param) => {
    return postRequest('/couponTemplate/save', param);
  },

  /** 停用某一版。🔴 没有删除 —— 删了历史券的规则就查不回来了 */
  disable: (couponCode, version) => {
    return postRequest(`/couponTemplate/disable?couponCode=${encodeURIComponent(couponCode)}&version=${version}`);
  },
};
