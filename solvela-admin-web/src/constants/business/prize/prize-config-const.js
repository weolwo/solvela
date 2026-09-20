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
  // dispatchable：这个类型「发得出去」——链路上该有的策略都注册齐了。
  // 对动账的类型是两层（consumer 的 @PrizeStrategy + ledger 的 @AssetStrategy），
  // 对不动账的 MARKER 只有一层就够（见下），所以判据是「链路完整」而不是「凑够两个注解」
  SCORE: { value: 'SCORE', desc: '积分', dispatchable: true },
  BALANCE: { value: 'BALANCE', desc: '现金', dispatchable: true },
  COUPON: { value: 'COUPON', desc: '优惠券', dispatchable: true },
  PHYSICAL: { value: 'PHYSICAL', desc: '实物', dispatchable: true },

  // 标记只有 @PrizeStrategy 一层，且这是【刻意的】：它不产生任何资产变动，
  // 所以 MarkerHandler 不生成提案，链路到 consumer 就结束了，根本走不到 ledger。
  // 缺 @AssetStrategy 在这里不是「没实现」，别照着别的类型给它补一个空壳。
  MARKER: { value: 'MARKER', desc: '标记', dispatchable: true },

  // 彩票 2026-09-18 补上了 LotteryPrizeHandler：中奖发一张号码到 t_lottery_record。
  // 它和 MARKER 一样只有 @PrizeStrategy 一层，且同样是刻意的 —— 一张号码不是资产，
  // 不动账也不进提案，所以 ledger 侧没有 @AssetStrategy，服务端那道
  // checkPromotionConfigMatch 也对它豁免（预算在 t_lottery_config.total_count 上，
  // 由领号引擎的 Redis 游标强制执行，不在优惠配置里）。
  //
  // 🔴 配置只填 prize_code = 彩票编码，【不填期号】：配奖品的那一刻，
  //    将来要发的那一期多半还不存在，发号时才挑当前在售的那一期。
  LOTTERY: { value: 'LOTTERY', desc: '彩票', dispatchable: true },

  // ⚠️ 自定义目前【两层策略都没有】—— 建出这类奖品，中奖后派发必定失败并写下
  // fail_reason「不支持的奖品类型」，而那是 AFTER_COMMIT 里的静默失败：
  // 抽奖照样返回 200 中奖，只有下游表才看得出没发出去。
  // 枚举保留（后端 PrizeTypeEnum 里有），但不进可选项，见 PRIZE_TYPE_OPTIONS。
  CUSTOM: { value: 'CUSTOM', desc: '自定义', dispatchable: false },
};

/**
 * 奖品类型可选项：只给能真正派发出去的类型。
 *
 * 为什么不是「给 CUSTOM 补上优惠配置就能选」：
 * 缺的不是优惠配置，是派发策略。补了配置只会让运营顺利建出一个必然发不出去的奖品，
 * 那比选不了更糟 —— 失败发生在 @TransactionalEventListener(AFTER_COMMIT) 里，
 * 对主链路完全无感，要翻 t_prize_log 的 fail_reason 才发现。
 *
 * 服务端其实已有一道隐式守卫：PrizeConfigService.checkPromotionConfigMatch 要求
 * 必须存在同 prizeType 的优惠配置，而 CUSTOM 一条都没有，直接 POST 也建不出来。
 * 这里只是别让运营撞上那道墙。
 *
 * ⚠️ 那道守卫对 MARKER 与 LOTTERY 是【豁免】的 —— 它们不动账、不进提案，
 * 所以「能不能选」的判据是<b>派发策略齐不齐</b>，不是「有没有优惠配置」。
 *
 * 将来补齐了对应的派发策略，把 dispatchable 改成 true 即可。
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
