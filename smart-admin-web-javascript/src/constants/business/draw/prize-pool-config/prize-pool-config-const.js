/**
 * 抽奖域-奖池配置表 枚举
 *
 * 取值对齐后端 t_prize_pool_config 的列注释。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 18:33:52
 * @Copyright  weolwo
 */

/**
 * 奖池状态：对齐 t_prize_pool_config.status
 */
export const POOL_STATUS_ENUM = {
  CLOSED: { value: 0, desc: '关闭', color: 'default' },
  OPEN: { value: 1, desc: '开启', color: 'green' },
};

export const POOL_STATUS_OPTIONS = Object.values(POOL_STATUS_ENUM).map((i) => ({ value: i.value, label: i.desc }));

/**
 * 消耗资产类型（抽奖门票）：对齐 t_prize_pool_config.cost_asset_type
 *
 * NONE 表示免费抽，此时 cost_value 无意义。
 */
export const COST_ASSET_TYPE_ENUM = {
  CREDIT: { value: 'CREDIT', desc: '积分' },
  TICKET: { value: 'TICKET', desc: '抽奖券' },
  NONE: { value: 'NONE', desc: '无消耗' },
};

export const COST_ASSET_TYPE_OPTIONS = Object.values(COST_ASSET_TYPE_ENUM).map((i) => ({ value: i.value, label: i.desc }));

/**
 * 重置周期：对齐 t_prize_pool_config.reset_period
 */
export const RESET_PERIOD_ENUM = {
  DAY: { value: 'DAY', desc: '每天' },
  WEEK: { value: 'WEEK', desc: '每周' },
  MONTH: { value: 'MONTH', desc: '每月' },
  ACTIVITY: { value: 'ACTIVITY', desc: '活动期间' },
};

export const RESET_PERIOD_OPTIONS = Object.values(RESET_PERIOD_ENUM).map((i) => ({ value: i.value, label: i.desc }));

/**
 * 抽奖算法：对齐 t_prize_pool_config.draw_mode
 *
 * 按概率 = 用 t_pool_prize_mapping.probability 直接抽；
 * 按库存比例 = 按各奖项剩余库存的占比抽，库存多的更容易中。
 */
export const DRAW_MODE_ENUM = {
  PROBABILITY: { value: 1, desc: '按概率' },
  STOCK_RATIO: { value: 2, desc: '按库存比例' },
};

export const DRAW_MODE_OPTIONS = Object.values(DRAW_MODE_ENUM).map((i) => ({ value: i.value, label: i.desc }));

export function poolStatusOf(value) {
  return Object.values(POOL_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function costAssetTypeOf(value) {
  return Object.values(COST_ASSET_TYPE_ENUM).find((i) => i.value === value)?.desc || value || '-';
}

export function resetPeriodOf(value) {
  return Object.values(RESET_PERIOD_ENUM).find((i) => i.value === value)?.desc || value || '-';
}

export function drawModeOf(value) {
  return Object.values(DRAW_MODE_ENUM).find((i) => i.value === value)?.desc || '-';
}

export default {
  POOL_STATUS_ENUM,
  POOL_STATUS_OPTIONS,
  COST_ASSET_TYPE_ENUM,
  COST_ASSET_TYPE_OPTIONS,
  RESET_PERIOD_ENUM,
  RESET_PERIOD_OPTIONS,
  DRAW_MODE_ENUM,
  DRAW_MODE_OPTIONS,
  poolStatusOf,
  costAssetTypeOf,
  resetPeriodOf,
  drawModeOf,
};
