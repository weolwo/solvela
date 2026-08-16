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

/*
 * ⚠️ COST_ASSET_TYPE_ENUM 已于 v3.64.0 随 cost_asset_type / cost_value 两个字段一起移除。
 *
 * 抽一次消耗什么、消耗多少属于业务规则，和「去哪个奖池抽」一样由上游算完再调进来，
 * 不是抽奖引擎该决定的事（彩票模块的 TicketIssueService 从一开始就是这么做的）。
 * 原先那个三选一还有个实际问题：TICKET 在运行态直接返回「暂未开放」，
 * CREDIT 恒定映射钱包 SCORE —— 下拉里能选三个，其中两个选了会失败或没区别。
 */

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

export function resetPeriodOf(value) {
  return Object.values(RESET_PERIOD_ENUM).find((i) => i.value === value)?.desc || value || '-';
}

export function drawModeOf(value) {
  return Object.values(DRAW_MODE_ENUM).find((i) => i.value === value)?.desc || '-';
}

export default {
  POOL_STATUS_ENUM,
  POOL_STATUS_OPTIONS,
  RESET_PERIOD_ENUM,
  RESET_PERIOD_OPTIONS,
  DRAW_MODE_ENUM,
  DRAW_MODE_OPTIONS,
  poolStatusOf,
  resetPeriodOf,
  drawModeOf,
};
