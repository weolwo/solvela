/**
 * 彩票模块 枚举与常量
 *
 * 与后端 net.lab1024.sa.lottery.constant.LotteryConst 同源，改动时两边必须一起改。
 *
 * @Author:    alaric
 * @Date:      2026-07-27
 */

/**
 * 号码长度下限 4，即号码空间不小于 1 万。
 * 三个理由叠加：小域下 FPE 分布质量差、cycle-walking 开销变大、1000 个号可被穷举
 * （而本系统的号码是可反解验真的，空间太小会让验真失去意义）。
 */
export const MIN_NUMBER_LENGTH = 4;

export const MAX_NUMBER_LENGTH = 9;

/**
 * 未中奖 / 未开奖的奖级占位值。
 * 用 99 而不是 null，是为了让 C 端「我的号码」能直接 ORDER BY prize_level ASC —— NULL 会排到最前面，正好排反。
 * 奖级规则里禁止配置该值，否则无法与「99 等奖」区分。
 */
export const PRIZE_LEVEL_NONE = 99;

/**
 * 匹配规则，取值对齐后端 MatchRuleEnum 与 t_lottery_prize_rule.match_rule
 */
export const MATCH_RULE_ENUM = {
  EXACT: { value: 'EXACT', desc: '精准匹配 (全号)' },
  TAIL: { value: 'TAIL', desc: '尾号匹配' },
  HEAD: { value: 'HEAD', desc: '首号匹配' },
};

export const MATCH_RULE_OPTIONS = Object.values(MATCH_RULE_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 彩票配置状态，对齐 t_lottery_config.status
 */
export const LOTTERY_STATUS_ENUM = {
  OFFLINE: { value: 0, desc: '未上线', color: 'default' },
  ONLINE: { value: 1, desc: '售卖中', color: 'green' },
};

export const LOTTERY_STATUS_OPTIONS = Object.values(LOTTERY_STATUS_ENUM).map((i) => ({ value: i.value, label: i.desc }));

/**
 * 期号状态，对齐 t_lottery_issue.status 与后端 IssueStatusEnum。
 *
 * ⚠️ 1 是「核销中」，不是列表页原先写的「售卖中」—— 开奖号码已定案且不可再改，
 * 正在分批比对号码。这一档是可恢复的断点标记：中断后重跑会接着比，不会重复发奖。
 * DDL 里的「部分开奖」是预留语义，本系统一期只开一次奖。
 * 售卖与否看的是 sale_start_time / sale_end_time，与这个状态是两个维度。
 */
export const ISSUE_STATUS_ENUM = {
  WAIT: { value: 0, desc: '待开奖', color: 'blue' },
  STAGED: { value: 1, desc: '核销中', color: 'orange' },
  OPENED: { value: 2, desc: '已开奖', color: 'green' },
};

export const ISSUE_STATUS_OPTIONS = Object.values(ISSUE_STATUS_ENUM).map((i) => ({ value: i.value, label: i.desc }));

/**
 * 购彩记录的中奖状态，对齐 t_lottery_record.win_status 与后端 TicketStatusEnum。
 *
 * ⚠️ 取值必须与后端一致：后端按该值做筛选校验，对不上会直接 400
 * （交接文档记过：TicketStatusEnum 的字段名写错曾让「按中奖状态筛选购彩记录」恒返回 400）。
 *
 * 注意 prize_level 用 99 表示未中奖，与这里的 win_status 是两个维度，别混。
 */
export const WIN_STATUS_ENUM = {
  WAIT: { value: 0, desc: '未开奖', color: 'default' },
  FAILURE_MATCH: { value: 1, desc: '未中奖', color: 'default' },
  SUCCESS_MATCH: { value: 2, desc: '已中奖', color: 'green' },
};

export const WIN_STATUS_OPTIONS = Object.values(WIN_STATUS_ENUM).map((i) => ({ value: i.value, label: i.desc }));

export function lotteryStatusOf(value) {
  return Object.values(LOTTERY_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function issueStatusOf(value) {
  return Object.values(ISSUE_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function winStatusOf(value) {
  return Object.values(WIN_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function matchRuleOf(value) {
  return MATCH_RULE_ENUM[value]?.desc || value || '-';
}

/**
 * 奖品类型对应的展示图标：资产池与奖级卡片共用，避免两处各写一份
 */
export const PRIZE_TYPE_ICON = {
  PHYSICAL: '📦',
  BALANCE: '💰',
  COUPON: '🎟️',
  SCORE: '⭐',
  LOTTERY: '🎫',
  CUSTOM: '🎁',
};

export function prizeIcon(prizeType) {
  return PRIZE_TYPE_ICON[prizeType] || '🎁';
}

export default {
  MIN_NUMBER_LENGTH,
  MAX_NUMBER_LENGTH,
  PRIZE_LEVEL_NONE,
  MATCH_RULE_ENUM,
  MATCH_RULE_OPTIONS,
  LOTTERY_STATUS_ENUM,
  WIN_STATUS_ENUM,
  PRIZE_TYPE_ICON,
  prizeIcon,
};
