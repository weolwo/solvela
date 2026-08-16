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
  // dispatchable：派发链路两层策略都齐了（consumer 的 @PrizeStrategy + ledger 的 @AssetStrategy），
  // 这样的奖品建出来才发得出去
  SCORE: { value: 'SCORE', desc: '积分', dispatchable: true },
  BALANCE: { value: 'BALANCE', desc: '现金', dispatchable: true },
  COUPON: { value: 'COUPON', desc: '优惠券', dispatchable: true },
  PHYSICAL: { value: 'PHYSICAL', desc: '实物', dispatchable: true },

  // ⚠️ 以下两类目前【两层策略都没有】—— 全工程 @PrizeStrategy 与 @AssetStrategy
  // 各只注册了 SCORE/BALANCE/COUPON/PHYSICAL 四个。
  // 建出这两类奖品，中奖后派发必定失败并写下 fail_reason「不支持的奖品类型」，
  // 而那是 AFTER_COMMIT 里的静默失败：抽奖照样返回 200 中奖，只有下游表才看得出没发出去。
  // 枚举保留（后端 PrizeTypeEnum 里有，且将来会实现），但不进可选项，见 PRIZE_TYPE_OPTIONS。
  LOTTERY: { value: 'LOTTERY', desc: '彩票', dispatchable: false },
  CUSTOM: { value: 'CUSTOM', desc: '自定义', dispatchable: false },
};

/**
 * 奖品类型可选项：只给能真正派发出去的类型。
 *
 * 为什么不是「给 LOTTERY/CUSTOM 补上优惠配置就能选」：
 * 缺的不是优惠配置，是派发策略。补了配置只会让运营顺利建出一个必然发不出去的奖品，
 * 那比选不了更糟 —— 失败发生在 @TransactionalEventListener(AFTER_COMMIT) 里，
 * 对主链路完全无感，要翻 t_prize_log 的 fail_reason 才发现。
 *
 * 服务端其实已有一道隐式守卫：PrizeConfigService.checkPromotionConfigMatch 要求
 * 必须存在同 prizeType 的优惠配置，而这两类一条都没有，直接 POST 也建不出来。
 * 这里只是别让运营撞上那道墙。
 *
 * 将来补齐了对应的 @PrizeStrategy + @AssetStrategy，把 dispatchable 改成 true 即可。
 */
export const PRIZE_TYPE_OPTIONS = Object.values(PRIZE_TYPE_ENUM)
  .filter((item) => item.dispatchable)
  .map((item) => ({
    value: item.value,
    label: `${item.desc}（${item.value}）`,
  }));

/** 全部类型（含不可派发的），供列表页回显历史数据用 —— 老数据里可能已经有这两类 */
export const PRIZE_TYPE_ALL_OPTIONS = Object.values(PRIZE_TYPE_ENUM).map((item) => ({
  value: item.value,
  label: `${item.desc}（${item.value}）`,
}));

/**
 * 奖品状态：对齐 t_prize_config.status。
 * 与活动的状态不同 —— 这里禁用是 0 而不是 2，别混用两套常量。
 */
export const PRIZE_STATUS_ENUM = {
  ENABLED: { value: 1, desc: '启用' },
  DISABLED: { value: 0, desc: '禁用' },
};

export const PRIZE_STATUS_OPTIONS = Object.values(PRIZE_STATUS_ENUM).map((s) => ({ value: s.value, label: s.desc }));

/**
 * 资产类型取值 -> 中文描述。表格里直接显示 SCORE/PHYSICAL 这种裸取值对运营没有意义。
 */
export function prizeTypeOf(value) {
  return Object.values(PRIZE_TYPE_ENUM).find((i) => i.value === value)?.desc || value || '-';
}

export function isPrizeEnabled(status) {
  return status === PRIZE_STATUS_ENUM.ENABLED.value;
}

/**
 * 审批模式：对齐 t_prize_config.approve_mode
 */
export const APPROVE_MODE_OPTIONS = [
  { value: 0, label: '自动免审' },
  { value: 1, label: '人工审批' },
];

export default {
  PRIZE_TYPE_ENUM,
  PRIZE_STATUS_ENUM,
  PRIZE_STATUS_OPTIONS,
  PRIZE_TYPE_OPTIONS,
  PRIZE_TYPE_ALL_OPTIONS,
  APPROVE_MODE_OPTIONS,
};
