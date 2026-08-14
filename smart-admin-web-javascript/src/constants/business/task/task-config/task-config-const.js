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

export const TARGET_AUDIENCE_OPTIONS = [
  { value: TARGET_AUDIENCE_ENUM.ALL, label: '全部会员' },
  { value: TARGET_AUDIENCE_ENUM.NEW_MEMBER, label: '新会员' },
  { value: TARGET_AUDIENCE_ENUM.OLD_MEMBER, label: '老会员' },
];

export function configStatusOf(value) {
  return Object.values(CONFIG_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function taskGroupOf(value) {
  return TASK_GROUP_OPTIONS.find((i) => i.value === value)?.label || value || '-';
}

export function limitTypeOf(value) {
  return LIMIT_TYPE_OPTIONS.find((i) => i.value === value)?.label || value || '-';
}

export function targetAudienceOf(value) {
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
  configStatusOf,
  taskGroupOf,
  limitTypeOf,
  targetAudienceOf,
};
