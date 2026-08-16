/**
 * 任务域-任务模板表 枚举
 *
 * 取值对齐后端 t_task_template 的列注释。
 *
 * ⚠️ 这里是流转类型字典的**唯一出处**，task-wizard-const.js 从本文件转出。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 16:56:38
 * @Copyright  weolwo
 */

/**
 * 流转类型：对齐 t_task_template.task_type
 *
 * 决定进度怎么累加：SIMPLE 一次到位，COUNT 按次数累加，AMOUNT 按金额累加。
 */
export const TASK_TYPE_ENUM = {
  SIMPLE: 'SIMPLE',
  COUNT: 'COUNT',
  AMOUNT: 'AMOUNT',
};

// label 带上英文取值：模板编码要跟后端脚本对齐，纯中文名反而对不上
export const TASK_TYPE_OPTIONS = [
  { value: TASK_TYPE_ENUM.SIMPLE, label: 'SIMPLE 单次节点型' },
  { value: TASK_TYPE_ENUM.COUNT, label: 'COUNT 计次型' },
  { value: TASK_TYPE_ENUM.AMOUNT, label: 'AMOUNT 计额型' },
];

/**
 * 模板状态：对齐 t_task_template.status（v3.61.0 新增列）
 *
 * 管理端用启停替代删除：模板被 t_task_config.template_code 引用，
 * 运行态还要按 code 取 ui_schema / rule_script，删掉不会立刻报错，
 * 只会让引用它的任务安静地不再推进。禁用后仅仅是不再出现在向导的候选模板里。
 */
export const TEMPLATE_STATUS_ENUM = {
  DISABLED: { value: 0, desc: '禁用', color: 'default' },
  ENABLED: { value: 1, desc: '启用', color: 'green' },
};

export const TEMPLATE_STATUS_OPTIONS = Object.values(TEMPLATE_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function templateStatusOf(value) {
  return Object.values(TEMPLATE_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

const TASK_TYPE_DESC = {
  SIMPLE: '单次节点型',
  COUNT: '计次型',
  AMOUNT: '计额型',
};

export function taskTypeOf(value) {
  return TASK_TYPE_DESC[value] || value || '-';
}

export default {
  TASK_TYPE_ENUM,
  TASK_TYPE_OPTIONS,
  TEMPLATE_STATUS_ENUM,
  TEMPLATE_STATUS_OPTIONS,
  taskTypeOf,
  templateStatusOf,
};
