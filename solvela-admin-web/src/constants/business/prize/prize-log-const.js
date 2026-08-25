/**
 * 奖励发放记录 枚举
 *
 * 取值对齐后端 t_prize_log 的列注释与 PrizeDispatchHandler 的常量。
 *
 * @Author:    alaric
 * @Date:      2026-07-28
 */

/**
 * 审批状态：对齐 t_prize_log.approve_status
 *
 * ⚠️ 本系统有两套并存的审批，语义不同、可叠加，看数据时务必分清：
 *   · 这里是 prize 域，判定依据 t_prize_config.approve_mode，
 *     回答的是「这个奖该不该发给这个人」（运营视角）；
 *   · 另一套是提案域 t_proposal_record.status，依据 t_promotion_config.review_level，
 *     回答的是「这笔钱该不该出」（财务视角）。
 * 两层都配了审批就需要运营和财务各批一次。
 */
export const APPROVE_STATUS_ENUM = {
  NONE: { value: 0, desc: '无需审批', color: 'default' },
  PENDING: { value: 1, desc: '待审批', color: 'orange' },
  PASSED: { value: 2, desc: '已批准', color: 'green' },
  REJECTED: { value: 3, desc: '已驳回', color: 'red' },
};

export const APPROVE_STATUS_OPTIONS = Object.values(APPROVE_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 执行状态：对齐 t_prize_log.status
 *
 * ⚠️ status=0 的正常含义只有一个：等待人工审批。
 * 若出现「非待审批却停在 0」，说明派发链路中断了 —— 那是需要排查的信号，不是正常态。
 */
export const DISPATCH_STATUS_ENUM = {
  WAITING: { value: 0, desc: '等待执行', color: 'blue' },
  SUCCESS: { value: 1, desc: '发放成功', color: 'green' },
  FAILED: { value: 2, desc: '发放失败', color: 'red' },
};

export const DISPATCH_STATUS_OPTIONS = Object.values(DISPATCH_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 奖励类型：t_prize_log.prize_type 与 t_proposal_record.asset_type 是<b>同一个字典</b>
 * （后端同一个 PrizeTypeEnum），所以直接复用提案域那一份，不在这里再抄一遍 ——
 * 抄一遍就是第二个真相源，将来改了名称或颜色两个页面会悄悄不一致。
 */
export { ASSET_TYPE_ENUM as PRIZE_TYPE_ENUM, assetTypeOf as prizeTypeOf } from '/src/constants/business/risk/proposal-record-const';

/**
 * 奖励体值的单位。
 *
 * ⚠️ 与提案域的 ASSET_UNIT 刻意不同，不要「统一」掉：
 * t_prize_log.prize_value 在四种类型下<b>都是金额/数值</b>——
 * 券是<b>面额</b>（CouponHandler 里那个变量就叫 amount，一次固定发一张券），
 * 实物是<b>价值</b>。所以 COUPON / PHYSICAL 的单位是元，不是「张 / 件」。
 * DDL 里「奖励体值(积分数/券ID)」那句列注释是早年的，已经和四个发奖策略的代码对不上了。
 */
export const PRIZE_VALUE_UNIT = {
  SCORE: '积分',
  BALANCE: '元',
  COUPON: '元',
  PHYSICAL: '元',
};

export function prizeValueUnitOf(value) {
  return PRIZE_VALUE_UNIT[value] || '';
}

export function approveStatusOf(value) {
  return Object.values(APPROVE_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function dispatchStatusOf(value) {
  return Object.values(DISPATCH_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export default {
  APPROVE_STATUS_ENUM,
  APPROVE_STATUS_OPTIONS,
  DISPATCH_STATUS_ENUM,
  DISPATCH_STATUS_OPTIONS,
  PRIZE_VALUE_UNIT,
  approveStatusOf,
  dispatchStatusOf,
  prizeValueUnitOf,
};
