/*
 * 商城-单品覆盖价
 *
 * 🔴 权限点用的是 mallCommodity:update，不是单独一个。
 *    能改商品价的人本来就能改这里的价 —— 拆成两个权限，会出现
 *    「能把商品从 100 改成 1，但不能给白金配 88」这种没有意义的授权组合。
 *
 * @author alaric
 * @date 2026-09-23
 */
import { getRequest, postRequest } from '/@/lib/axios';

export const mallGradePriceApi = {
  /**
   * 一件商品的全部覆盖价，按等级、再按规格排  @author alaric
   */
  list: (commodityId) => {
    return getRequest(`/mallGradePrice/list/${commodityId}`);
  },

  /**
   * 新增 / 编辑一行。同一个（商品, 规格, 等级）是改价，不是再插一行  @author alaric
   */
  save: (param) => {
    return postRequest('/mallGradePrice/save', param);
  },

  /**
   * 删一行。⚠️ 删掉之后这一档落回【等级折扣率】，不是落回原价  @author alaric
   */
  delete: (id) => {
    return getRequest(`/mallGradePrice/delete/${id}`);
  },
};
