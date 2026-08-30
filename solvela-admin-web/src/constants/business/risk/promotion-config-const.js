/**
 * 风控域-优惠配置表 枚举
 *
 * 取值对齐后端 t_promotion_config 的列注释。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:19:16
 * @Copyright  weolwo
 */

/**
 * 配置状态：对齐 t_promotion_config.status
 *
 * PromotionConfigService 只捞 status=1 的配置，停用即等于该预算池不再出账。
 */
export const PROMOTION_STATUS_ENUM = {
  DISABLED: { value: 0, desc: '停用', color: 'default' },
  ENABLED: { value: 1, desc: '启用', color: 'green' },
};

export const PROMOTION_STATUS_OPTIONS = Object.values(PROMOTION_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 资产类型：对齐 t_promotion_config.prize_type，与后端 PrizeTypeEnum 同一字典
 *
 * 库存(total_quota)与预算(total_amount)是二选一用的：券/实物看库存，积分/现金看预算，
 * 不适用的那个填 -1 表示不限制。
 */
export const PRIZE_TYPE_ENUM = {
  SCORE: { value: 'SCORE', desc: '积分', color: 'blue' },
  BALANCE: { value: 'BALANCE', desc: '现金', color: 'green' },
  COUPON: { value: 'COUPON', desc: '优惠券', color: 'orange' },
  PHYSICAL: { value: 'PHYSICAL', desc: '实物', color: 'purple' },

  // ⚠️ 这里【没有】MARKER，是刻意的：标记类奖品不动账、不进提案，
  // 预算 / 库存 / 风控频次 / 审批阈值这四样它一个都用不上，挂优惠配置纯属多余。
  // t_prize_config.promotion_config_id 已改为可空，服务端
  // PrizeConfigService.checkPromotionConfigMatch 对 MARKER 直接放行。
  // 别为了「字典要对齐 PrizeTypeEnum」把它加回来 —— 加回来只会让运营建出
  // 一堆永远不会被消耗的空池子。
};

export const PRIZE_TYPE_OPTIONS = Object.values(PRIZE_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 「不限制」哨兵值：total_quota / total_amount / 各 xxx_limit 都用 -1 表示不限。
 */
export const UNLIMITED = -1;

/**
 * 按「个数」控量的资产类型 —— 看 total_quota / used_quota。
 * 其余（积分、现金）按「金额」控量，看 total_amount / used_amount。
 *
 * 两侧是二选一的：不适用的那一侧填 -1。判据集中放这里，
 * 免得表单、列表、详情各写一遍 `prizeType === 'COUPON' || ...`。
 */
export const QUOTA_BASED_PRIZE_TYPES = [PRIZE_TYPE_ENUM.COUPON.value, PRIZE_TYPE_ENUM.PHYSICAL.value];

export function isQuotaBased(prizeType) {
  return QUOTA_BASED_PRIZE_TYPES.includes(prizeType);
}

/**
 * 审核层级：对齐 t_promotion_config.review_level
 *
 * 决定提案走几道审批，与 t_proposal_record.status 的 10/11 两档直接对应，见 [[proposal-record-const]]。
 */
export const REVIEW_LEVEL_ENUM = {
  NONE: { value: 0, desc: '无需审核', color: 'default' },
  SINGLE: { value: 1, desc: '单层审批', color: 'blue' },
  DOUBLE: { value: 2, desc: '双层审批', color: 'orange' },
};

export const REVIEW_LEVEL_OPTIONS = Object.values(REVIEW_LEVEL_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 限制周期：对齐 t_promotion_config.limit_period
 *
 * 下面那一组 xxx_limit（会员/手机号/IP/设备/指纹）都是"同一个周期内"的次数上限，
 * 周期换了，计数就重新开始。
 */
export const LIMIT_PERIOD_ENUM = {
  LIFETIME: { value: 'LIFETIME', desc: '终身', color: 'default' },
  DAILY: { value: 'DAILY', desc: '每日', color: 'blue' },
  WEEKLY: { value: 'WEEKLY', desc: '每周', color: 'cyan' },
  MONTHLY: { value: 'MONTHLY', desc: '每月', color: 'purple' },
  CUSTOM: { value: 'CUSTOM', desc: '自定义', color: 'gold' },
};

export const LIMIT_PERIOD_OPTIONS = Object.values(LIMIT_PERIOD_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function promotionStatusOf(value) {
  return Object.values(PROMOTION_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function prizeTypeOf(value) {
  return Object.values(PRIZE_TYPE_ENUM).find((i) => i.value === value) || { desc: value || '-', color: 'default' };
}

export function reviewLevelOf(value) {
  return Object.values(REVIEW_LEVEL_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function limitPeriodOf(value) {
  return Object.values(LIMIT_PERIOD_ENUM).find((i) => i.value === value) || { desc: value || '-', color: 'default' };
}

export default {
  PROMOTION_STATUS_ENUM,
  PROMOTION_STATUS_OPTIONS,
  PRIZE_TYPE_ENUM,
  PRIZE_TYPE_OPTIONS,
  UNLIMITED,
  QUOTA_BASED_PRIZE_TYPES,
  isQuotaBased,
  REVIEW_LEVEL_ENUM,
  REVIEW_LEVEL_OPTIONS,
  LIMIT_PERIOD_ENUM,
  LIMIT_PERIOD_OPTIONS,
  promotionStatusOf,
  prizeTypeOf,
  reviewLevelOf,
  limitPeriodOf,
};
