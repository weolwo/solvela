/**
 * 任务域-任务配置表 枚举
 *
 * 取值对齐后端 t_task_config 的列注释与 TaskConst 里的状态常量。
 *
 * ⚠️ 这里是这几本字典的**唯一出处**，task-wizard-const.js 从本文件转出 ——
 * 向导与列表页配的是同一张表，字典抄两份迟早会漂。
 *
 * ⚠️ 触发事件（trigger_event）刻意不在这里定义：它是**开放集合**，
 * 由服务端注册表 t_task_event 下发（taskApi.queryEventOptionList()），
 * 加一个事件应该只是加一行数据 + 上游埋点，前端零改动。详见 task-wizard-const.js 的说明。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 16:59:38
 * @Copyright  weolwo
 */

/**
 * 任务配置状态：对齐 t_task_config.status 与后端 TaskConst.CONFIG_STATUS_*
 *
 * 🔴 运行态的订阅判据是「status != 3」，不是「status == 2」——
 * 全工程没有任何地方把 status 从 1 改成 2（wizardSubmit 落的就是 1，也没有「启用」接口），
 * 所以「待生效」实际上就是可用状态，别被字面意思误导。
 *
 * DDL 的默认值是 0，而注释里没有 0 这个取值；后端判 != 3 时把它一并当作可用，
 * 这里也给它一个显式档位，免得列表上出现空白的状态列。
 */
export const CONFIG_STATUS_ENUM = {
  DEFAULT: { value: 0, desc: '待生效', color: 'blue' },
  PENDING: { value: 1, desc: '待生效', color: 'blue' },
  ACTIVE: { value: 2, desc: '生效中', color: 'green' },
  OFFLINE: { value: 3, desc: '已下线', color: 'default' },
};

// 0 与 1 同义，下拉里只保留 1，避免运营看见两个「待生效」
export const CONFIG_STATUS_OPTIONS = [CONFIG_STATUS_ENUM.PENDING, CONFIG_STATUS_ENUM.ACTIVE, CONFIG_STATUS_ENUM.OFFLINE].map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 任务分组：对齐 t_task_config.task_group
 */
export const TASK_GROUP_ENUM = {
  NEWBIE: 'NEWBIE',
  DAILY: 'DAILY',
  PROMO: 'PROMO',
  VIP: 'VIP',
};

export const TASK_GROUP_OPTIONS = [
  { value: TASK_GROUP_ENUM.NEWBIE, label: '新手' },
  { value: TASK_GROUP_ENUM.DAILY, label: '日常' },
  { value: TASK_GROUP_ENUM.PROMO, label: '大促' },
  { value: TASK_GROUP_ENUM.VIP, label: '会员专属' },
];

/**
 * 参与频次：对齐 t_task_config.limit_type
 *
 * 只有 DAILY / WEEKLY 受 limit_count 轮次限制，与后端 TaskPeriodResolver.supportsRoundLimit 同一口径。
 */
export const LIMIT_TYPE_ENUM = {
  ONCE: 'ONCE',
  DAILY: 'DAILY',
  WEEKLY: 'WEEKLY',
  UNLIMITED: 'UNLIMITED',
};

export const LIMIT_TYPE_OPTIONS = [
  { value: LIMIT_TYPE_ENUM.ONCE, label: '终身一次' },
  { value: LIMIT_TYPE_ENUM.DAILY, label: '每日重复' },
  { value: LIMIT_TYPE_ENUM.WEEKLY, label: '每周重复' },
  { value: LIMIT_TYPE_ENUM.UNLIMITED, label: '无限制' },
];

/**
 * 目标人群：对齐 t_task_config.target_audience
 */
export const TARGET_AUDIENCE_ENUM = {
  ALL: 'ALL',
  NEW_MEMBER: 'NEW_MEMBER',
  OLD_MEMBER: 'OLD_MEMBER',
};

/**
 * 「等级 ≥ N」人群的取值前缀，整串形如 GRADE_GTE_2。对齐后端 TaskConst.AUDIENCE_GRADE_GTE_PREFIX。
 *
 * 🔴 等级不能穷举成枚举：它是配置，运营随时加一档。所以门槛值编在取值串里，
 *    页面上的可选项必须来自 memberGradeApi.listConfig()，不能在这里写死。
 */
export const AUDIENCE_GRADE_GTE_PREFIX = 'GRADE_GTE_';

/** 单选按钮里代表「等级门槛」这一档的哨兵值。它不是 target_audience 的合法取值，只用于 UI */
export const AUDIENCE_MODE_GRADE = 'LEVEL';

export const TARGET_AUDIENCE_OPTIONS = [
  { value: TARGET_AUDIENCE_ENUM.ALL, label: '全部会员' },
  { value: TARGET_AUDIENCE_ENUM.NEW_MEMBER, label: '新会员' },
  { value: TARGET_AUDIENCE_ENUM.OLD_MEMBER, label: '老会员' },
  { value: AUDIENCE_MODE_GRADE, label: '等级门槛' },
];

/** GRADE_GTE_2 -> 2；不是这个形状返回 null。与后端 TaskConst.levelThresholdOf 同口径 */
export function audienceGradeOf(value) {
  if (typeof value !== 'string' || !value.startsWith(AUDIENCE_GRADE_GTE_PREFIX)) {
    return null;
  }
  const raw = value.slice(AUDIENCE_GRADE_GTE_PREFIX.length);
  // 只认纯数字：GRADE_GTE_2.5 / GRADE_GTE_ 2 / GRADE_GTE_-1 都按写坏处理，
  // 与后端一致 —— 猜一个数出来会让「配错的等级专享」静默变成「所有人可做」
  if (!/^\d+$/.test(raw)) {
    return null;
  }
  return Number(raw);
}

export function buildAudienceGrade(level) {
  return `${AUDIENCE_GRADE_GTE_PREFIX}${level}`;
}

export function configStatusOf(value) {
  return Object.values(CONFIG_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function taskGroupOf(value) {
  return TASK_GROUP_OPTIONS.find((i) => i.value === value)?.label || value || '-';
}

export function limitTypeOf(value) {
  return LIMIT_TYPE_OPTIONS.find((i) => i.value === value)?.label || value || '-';
}

/**
 * 人群取值 -> 中文。
 *
 * @param value      t_task_config.target_audience 的原值
 * @param gradeNames 等级号 -> 等级名，可选。传了就显示「银卡会员及以上」，
 *                   没传退化成「等级 2 及以上」—— 退化态也要是人话，
 *                   直接显示 GRADE_GTE_2 等于把库里的取值糊在页面上
 */
export function targetAudienceOf(value, gradeNames) {
  const level = audienceGradeOf(value);
  if (level !== null) {
    const name = gradeNames?.[level];
    return name ? `${name}及以上` : `等级 ${level} 及以上`;
  }
  return TARGET_AUDIENCE_OPTIONS.find((i) => i.value === value)?.label || value || '-';
}

export default {
  CONFIG_STATUS_ENUM,
  CONFIG_STATUS_OPTIONS,
  TASK_GROUP_ENUM,
  TASK_GROUP_OPTIONS,
  LIMIT_TYPE_ENUM,
  LIMIT_TYPE_OPTIONS,
  TARGET_AUDIENCE_ENUM,
  TARGET_AUDIENCE_OPTIONS,
  AUDIENCE_GRADE_GTE_PREFIX,
  AUDIENCE_MODE_GRADE,
  audienceGradeOf,
  buildAudienceGrade,
  configStatusOf,
  taskGroupOf,
  limitTypeOf,
  targetAudienceOf,
};
