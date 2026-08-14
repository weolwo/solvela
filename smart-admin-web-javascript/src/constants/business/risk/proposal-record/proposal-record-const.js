/**
 * 风控域-发奖提案表 枚举
 *
 * 取值对齐后端 t_proposal_record 的列注释与 ProposalRecordService 里的状态常量。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:20:02
 * @Copyright  weolwo
 */

/**
 * 提案状态：对齐 t_proposal_record.status
 *
 * ⚠️ 这是**财务视角**的审批链（依据 t_promotion_config.review_level，回答「这笔钱该不该出」），
 * 与 prize 域的 t_prize_log.approve_status（运营视角：「这个奖该不该发给这个人」）是两套并存的审批，
 * 看数据时务必分清。详见 [[prize-log-const]]。
 *
 * 取值刻意留出间隔（0/10/11/20/30…）：审批链中间插状态是常事，
 * 连续编号会逼着后来的人把新状态排到末尾，看数字就再也读不出流程顺序。
 */
export const PROPOSAL_STATUS_ENUM = {
  WAITING: { value: 0, desc: '等待中', color: 'default' },
  FIRST_REVIEW: { value: 10, desc: '待一审', color: 'orange' },
  SECOND_REVIEW: { value: 11, desc: '待二审', color: 'orange' },
  REJECTED: { value: 20, desc: '驳回', color: 'red' },
  PENDING_EXECUTE: { value: 30, desc: '待执行', color: 'blue' },
  EXECUTING: { value: 40, desc: '执行中', color: 'processing' },
  SUCCESS: { value: 50, desc: '成功', color: 'green' },
  PARTIAL: { value: 60, desc: '部分成功', color: 'gold' },
  FAILED: { value: 70, desc: '彻底失败', color: 'red' },
  BLOCKED: { value: 80, desc: '风控拦截', color: 'volcano' },
};

export const PROPOSAL_STATUS_OPTIONS = Object.values(PROPOSAL_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 提案来源：对齐 t_proposal_record.source_type 与后端 ProposalSourceTypeEnum
 *
 * 取值由 ProposalSourceResolver 从活动类型推导 —— 「这个奖来自哪种玩法」
 * 本来就等于 t_activity_config.activity_type。
 */
export const PROPOSAL_SOURCE_TYPE_ENUM = {
  TASK: { value: 'TASK', desc: '任务', color: 'blue' },
  DRAW: { value: 'DRAW', desc: '抽奖', color: 'purple' },
  LOTTERY: { value: 'LOTTERY', desc: '彩票', color: 'cyan' },
  MANUAL: { value: 'MANUAL', desc: '人工', color: 'default' },
};

export const PROPOSAL_SOURCE_TYPE_OPTIONS = Object.values(PROPOSAL_SOURCE_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 资产类型：对齐 t_proposal_record.asset_type，与后端 PrizeTypeEnum 同一字典
 */
export const ASSET_TYPE_ENUM = {
  SCORE: { value: 'SCORE', desc: '积分', color: 'blue' },
  BALANCE: { value: 'BALANCE', desc: '现金', color: 'green' },
  COUPON: { value: 'COUPON', desc: '优惠券', color: 'orange' },
  PHYSICAL: { value: 'PHYSICAL', desc: '实物', color: 'purple' },
};

export const ASSET_TYPE_OPTIONS = Object.values(ASSET_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function proposalStatusOf(value) {
  return Object.values(PROPOSAL_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function proposalSourceTypeOf(value) {
  return Object.values(PROPOSAL_SOURCE_TYPE_ENUM).find((i) => i.value === value) || { desc: value || '-', color: 'default' };
}

export function assetTypeOf(value) {
  return Object.values(ASSET_TYPE_ENUM).find((i) => i.value === value) || { desc: value || '-', color: 'default' };
}

export default {
  PROPOSAL_STATUS_ENUM,
  PROPOSAL_STATUS_OPTIONS,
  PROPOSAL_SOURCE_TYPE_ENUM,
  PROPOSAL_SOURCE_TYPE_OPTIONS,
  ASSET_TYPE_ENUM,
  ASSET_TYPE_OPTIONS,
  proposalStatusOf,
  proposalSourceTypeOf,
  assetTypeOf,
};
