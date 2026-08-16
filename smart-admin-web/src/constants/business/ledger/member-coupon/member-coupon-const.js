/**
 * 资产域-会员优惠券实例表 枚举
 *
 * 取值对齐后端 t_member_coupon 的列注释。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:15:39
 * @Copyright  weolwo
 */

/**
 * 券状态：对齐 t_member_coupon.status
 *
 * 发券时落 0-未使用（见 CouponAssetHandler），核销后置 1 并回填 used_time。
 */
export const COUPON_STATUS_ENUM = {
  UNUSED: { value: 0, desc: '未使用', color: 'blue' },
  USED: { value: 1, desc: '已使用', color: 'green' },
  EXPIRED: { value: 2, desc: '已过期', color: 'default' },
  CANCELED: { value: 3, desc: '已作废', color: 'red' },
};

export const COUPON_STATUS_OPTIONS = Object.values(COUPON_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function couponStatusOf(value) {
  return Object.values(COUPON_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export default {
  COUPON_STATUS_ENUM,
  COUPON_STATUS_OPTIONS,
  couponStatusOf,
};
