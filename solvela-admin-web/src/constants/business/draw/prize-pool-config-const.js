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
 * 重置周期：对齐 <b>t_draw_config.reset_period</b>。
 *
 * ⚠️ 它曾经在 t_prize_pool_config 上，那是挂错了层 —— 重置周期是玩法级的，
 * 不是某个奖池自己的事。现在住在抽奖配置上，奖池页只展示不编辑。
 *
 * 它决定两件事，由同一个开关驱动：
 *   ① 奖项「单人限领次数」的计数记在哪个周期桶（Redis key）
 *   ② 统计「本轮已经抽了几次」时从哪天起算（SQL 的 create_time 下界）
 */
export const RESET_PERIOD_ENUM = {
  DAY: { value: 'DAY', desc: '每天' },
  WEEK: { value: 'WEEK', desc: '每周' },
  MONTH: { value: 'MONTH', desc: '每月' },
  ACTIVITY: { value: 'ACTIVITY', desc: '活动期间' },
};

export const RESET_PERIOD_OPTIONS = Object.values(RESET_PERIOD_ENUM).map((i) => ({ value: i.value, label: i.desc }));

/**
 * 抽奖算法：对齐 <b>t_draw_config.draw_mode</b>（从奖池上移过来）。
 *
 * 按概率 = 用 t_pool_prize_mapping.probability 直接抽；
 * 按库存比例 = 按各奖项剩余库存的占比抽，库存多的更容易中。
 *
 * 🔴 <b>STOCK_RATIO 至今没有实现</b>：DrawEngine 只做了「按概率」，选它照样按概率抽。
 * 所以它在下拉里是 <b>disabled</b> 的（见 DRAW_MODE_SELECTABLE_OPTIONS）——
 * 字段可见但选不中，而不是藏起来假装没有。
 *
 * 曾经因为「免得运营配一个假开关」把整个字段从界面摘掉过；搬到抽奖配置之后
 * 字段重新露面，那条理由依然成立，所以用禁用而不是重新放开。
 */
export const DRAW_MODE_ENUM = {
  PROBABILITY: { value: 1, desc: '按概率' },
  STOCK_RATIO: { value: 2, desc: '按库存比例' },
};

export const DRAW_MODE_OPTIONS = Object.values(DRAW_MODE_ENUM).map((i) => ({ value: i.value, label: i.desc }));

/**
 * 给下拉用：未实现的算法禁选。
 *
 * 与 DRAW_MODE_OPTIONS 分开，是因为列表页展示存量值时不该受禁用影响 ——
 * 库里真有一行是 STOCK_RATIO 的话，它得能显示出来。
 */
export const DRAW_MODE_SELECTABLE_OPTIONS = Object.values(DRAW_MODE_ENUM).map((i) => ({
  value: i.value,
  label: i.value === DRAW_MODE_ENUM.STOCK_RATIO.value ? `${i.desc}（尚未实现）` : i.desc,
  disabled: i.value === DRAW_MODE_ENUM.STOCK_RATIO.value,
}));

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
  DRAW_MODE_SELECTABLE_OPTIONS,
  poolStatusOf,
  resetPeriodOf,
  drawModeOf,
};
