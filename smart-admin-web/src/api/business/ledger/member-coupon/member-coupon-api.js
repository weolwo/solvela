/**
 * 资产域-会员优惠券实例表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:15:39
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const memberCouponApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/memberCoupon/queryPage', param);
  },

  /**
   * 优惠券统计：本期发放、本期核销、券库存与过期体检。
   *
   * ⚠️ 发放和核销是**两个口径**：发放按 create_time，核销按 used_time。
   * 两个数没有包含关系，页面上不要相减 —— 今天核销的券可能是上个月发的。
   *
   * ⚠️ stock 开头的那几个字段是**全量**，不随时间范围变化：
   * 压着多少张没用的券是存量问题，限制在今天只会把它藏起来  @author  alaric
   */
  stat: (param) => {
    return postRequest('/memberCoupon/stat', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/memberCoupon/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/memberCoupon/update', param);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/memberCoupon/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/memberCoupon/batchDelete', idList);
  },
};
