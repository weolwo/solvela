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

/**
 * 人工发券 api 封装。
 *
 * 🔴 和券模板刻意分开一个对象：配规则和**直接给用户发钱**是两件事，
 * 权限点也是分开的（couponTemplate:save vs manualCoupon:send）。
 * 放在一起的话，下次有人给整个对象加一个「批量」方法时不会意识到自己越了哪条线。
 */
export const manualCouponApi = {
  /**
   * 发券。
   *
   * @param param.memberIds   会员号数组
   * @param param.memberNames 会员账号数组。查不到会整批拒绝，不是跳过
   * @param param.couponCode  券模编码。必须有启用中的模板，否则服务端直接拒
   * @param param.quantity    每人几张，默认 1，上限 10
   * @param param.bizRefId    工单号 / 批次号。🔴 防重发的唯一依据，不能为空
   * @param param.reason      发券原因，会原样显示给用户
   * @returns { granted, skipped, failed, couponName }
   *          —— skipped 是「本来就发过」，那是幂等不是失败，两者必须分得开
   */
  send: (param) => {
    return postRequest('/manualCoupon/send', param);
  },
};

export const couponTemplateApi = {
  /** 模板列表，每个券编码取最新启用版 */
  list: () => {
    return getRequest('/couponTemplate/list');
  },

  /** 某个券编码的全部历史版本，新的在前 */
  versions: (couponCode) => {
    return getRequest(`/couponTemplate/versions?couponCode=${encodeURIComponent(couponCode)}`);
  },

  /**
   * 体检：会发券但没有模板的配置点。
   *
   * 🔴 发券侧找不到模板时是**照发**的（规则列全空），不是拒发 ——
   * 拒发会在运行期把一个在架商品变成兑换必失败。但降级必须看得见，
   * 否则就成了「不报错，只是没生效」：券照发、用户照收，
   * 直到有人拿它去抵扣才发现减不出钱。这个接口就是那半个「看得见」。
   */
  missing: () => {
    return getRequest('/couponTemplate/missing');
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
