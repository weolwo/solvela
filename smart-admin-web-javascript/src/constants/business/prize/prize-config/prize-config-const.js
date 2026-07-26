/**
 * 业务级-发奖规则与奖品明细表 枚举
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 18:39:36
 * @Copyright  weolwo
 */

/**
 * 资产类型，取值对齐后端 PrizeTypeEnum 与 t_promotion_config.prize_type。
 * 奖品配置表单靠它做「奖品类型 -> 优惠配置」的级联过滤，两边取值必须完全一致
 */
export const PRIZE_TYPE_ENUM = {
  SCORE: { value: 'SCORE', desc: '积分' },
  BALANCE: { value: 'BALANCE', desc: '现金' },
  COUPON: { value: 'COUPON', desc: '优惠券' },
  PHYSICAL: { value: 'PHYSICAL', desc: '实物' },
  LOTTERY: { value: 'LOTTERY', desc: '彩票' },
  CUSTOM: { value: 'CUSTOM', desc: '自定义' },
};

export const PRIZE_TYPE_OPTIONS = Object.values(PRIZE_TYPE_ENUM).map((item) => ({
  value: item.value,
  label: `${item.desc}（${item.value}）`,
}));

/**
 * 审批模式：对齐 t_prize_config.approve_mode
 */
export const APPROVE_MODE_OPTIONS = [
  { value: 0, label: '自动免审' },
  { value: 1, label: '人工审批' },
];

export default {
  PRIZE_TYPE_ENUM,
  PRIZE_TYPE_OPTIONS,
  APPROVE_MODE_OPTIONS,
};
