/**
 * 业务级-任务阶段与奖励映射表 枚举
 *
 * 取值对齐后端 t_task_prize_mapping 的列注释。
 *
 * ⚠️ 这里是计算类型字典的**唯一出处**，task-wizard-const.js 从本文件转出 ——
 * 向导的奖励阶梯写的就是这张表。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:07:07
 * @Copyright  weolwo
 */

/**
 * 计算类型：对齐 t_task_prize_mapping.prize_mode
 *
 * 决定 prize_strategy 里该放什么：
 *   FIXED   固定值，如 {"amount": 20}
 *   RATIO   按比例，如 {"ratio": 0.05}
 *   FORMULA 表达式，交给规则引擎算
 */
export const PRIZE_MODE_ENUM = {
  FIXED: 'FIXED',
  RATIO: 'RATIO',
  FORMULA: 'FORMULA',
};

// label 带上英文取值：策略 JSON 要跟后端对齐，纯中文名对不上
export const PRIZE_MODE_OPTIONS = [
  { value: PRIZE_MODE_ENUM.FIXED, label: '固定 FIXED' },
  { value: PRIZE_MODE_ENUM.RATIO, label: '比例 RATIO' },
  { value: PRIZE_MODE_ENUM.FORMULA, label: '公式 FORMULA' },
];

const PRIZE_MODE_DESC = {
  FIXED: '固定',
  RATIO: '比例',
  FORMULA: '公式',
};

export function prizeModeOf(value) {
  return PRIZE_MODE_DESC[value] || value || '-';
}

export default {
  PRIZE_MODE_ENUM,
  PRIZE_MODE_OPTIONS,
  prizeModeOf,
};
