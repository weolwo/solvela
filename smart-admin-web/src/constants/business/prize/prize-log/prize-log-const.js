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
  approveStatusOf,
  dispatchStatusOf,
};
