/**
 * 任务配置向导 常量与枚举（消除魔法值的唯一出处）
 *
 * @Author:    alaric
 * @Date:      2026-07-19
 */

// ---------------------------- 页面路由（与菜单配置的「路由地址」保持一致） ----------------------------

export const TASK_CONFIG_LIST_PATH = '/business/task/task-config';

// ---------------------------- 向导步骤 ----------------------------

export const WIZARD_STEP = {
  BASE: 0, // 模板与基础信息
  RULE: 1, // 达标规则（ui_schema 动态表单）
  PRIZE: 2, // 奖励阶梯
  AUDIENCE: 3, // 受众与时间
  SUMMARY: 4, // 预览提交
};

export const WIZARD_STEP_ITEMS = [
  { title: '模板与基础信息' },
  { title: '达标规则' },
  { title: '奖励阶梯' },
  { title: '受众与时间' },
  { title: '预览提交' },
];

// ---------------------------- ui_schema 控件类型 ----------------------------

export const WIDGET_TYPE = {
  NUMBER: 'number',
  TEXT: 'text',
  SELECT: 'select',
  RADIO: 'radio',
  SWITCH: 'switch',
  SLIDER: 'slider',
  IMAGE_UPLOAD: 'image_upload',
};

// ---------------------------- 任务类型 ----------------------------

export const TASK_TYPE_ENUM = {
  SIMPLE: 'SIMPLE',
  COUNT: 'COUNT',
  AMOUNT: 'AMOUNT',
};

export const TASK_TYPE_OPTIONS = [
  { value: TASK_TYPE_ENUM.SIMPLE, label: 'SIMPLE 单次节点型' },
  { value: TASK_TYPE_ENUM.COUNT, label: 'COUNT 计次型' },
  { value: TASK_TYPE_ENUM.AMOUNT, label: 'AMOUNT 计额型' },
];

// ---------------------------- 触发事件 ----------------------------

/**
 * ⚠️ 触发事件的写死常量已删除，改由服务端 t_task_event 注册表下发
 * （`taskApi.queryEventOptionList()` → `GET /taskEvent/optionList`）。
 *
 * 为什么不留在这里：事件是**开放集合**，加一个「分享商品」要改前端常量、改 DDL 注释、
 * 可能还要加后端枚举 —— 与「新增任务模板前端零改动」的目标正面冲突。
 * 现在加事件 = 加一行数据 + 上游埋点，前端一行都不用动。
 *
 * 顺带说明：原常量里的 `CUSTOM（自定义埋点）` **刻意没有迁进注册表** ——
 * 它是个占位符而不是真实事件，选中它的任务永远不会有上游触发，
 * 留着只会让运营配出一个安静地不动的任务。
 *
 * 把服务端下发的选项转成 a-select 需要的 { value, label } 形态。
 * @param {Array} list `/taskEvent/optionList` 的返回
 */
export function toEventOptions(list) {
  return (list || []).map((item) => ({
    value: item.eventCode,
    // 编码同时显示：运营配任务时要把编码告诉上游开发，纯中文名反而对不上
    label: `${item.eventName}（${item.eventCode}）`,
    bizIdRequired: item.bizIdRequired,
    metricSource: item.metricSource,
  }));
}

// ---------------------------- 任务分组 ----------------------------

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

// ---------------------------- 参与频次 ----------------------------

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

export const DEFAULT_LIMIT_COUNT = 1;

// ---------------------------- 目标人群 ----------------------------

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

// ---------------------------- 奖励阶梯 ----------------------------

export const PRIZE_MODE_ENUM = {
  FIXED: 'FIXED',
  RATIO: 'RATIO',
  FORMULA: 'FORMULA',
};

export const PRIZE_MODE_OPTIONS = [
  { value: PRIZE_MODE_ENUM.FIXED, label: '固定 FIXED' },
  { value: PRIZE_MODE_ENUM.RATIO, label: '比例 RATIO' },
  { value: PRIZE_MODE_ENUM.FORMULA, label: '公式 FORMULA' },
];

// 奖品编码（实际应由 t_prize_config 接口下发）
export const PRIZE_CODE_OPTIONS = [
  { value: 'SCORE_100', label: '积分 · 100分' },
  { value: 'COUPON_100_20', label: '优惠券 · 满100减20' },
  { value: 'CASH_RANDOM', label: '现金红包 · 随机' },
  { value: 'PHYSICAL_LIMITED', label: '实物 · 限量周边' },
];

export const DEFAULT_PRIZE_LADDER = {
  stageCondition: 1,
  prizeCode: 'SCORE_100',
  prizeMode: PRIZE_MODE_ENUM.FIXED,
  prizeValue: 100,
};

// 阶梯至少保留 1 级，不允许删空
export const MIN_PRIZE_LADDER_COUNT = 1;

// 达标条件数值的单位随任务类型变化
export const STAGE_CONDITION_UNIT = {
  SIMPLE: '',
  COUNT: '次',
  AMOUNT: '元',
};

// ---------------------------- 其他默认值 ----------------------------

export const DEFAULT_SORT_WEIGHT = 60;

// ---------------------------- ui_schema visibleWhen 判定 ----------------------------
// 契约主形态为 { field, eq }，兼容 { key, value } 写法；渲染器显隐与向导分步校验共用此判定
export function isSchemaParamVisible(param, values) {
  const condition = param.visibleWhen;
  if (!condition) {
    return true;
  }
  const field = condition.field ?? condition.key;
  const expected = 'eq' in condition ? condition.eq : condition.value;
  return values?.[field] === expected;
}

// 按 widget 类型拆分 schema 参数值：image_upload -> uiValues（入 ui_config），其余 -> ruleValues（入 rule_config）
// 向导提交组装与设计器实时预览共用，保证两处拆分语义永远一致
export function splitSchemaValues(params, values) {
  const ruleValues = {};
  const uiValues = {};
  (params || []).forEach((p) => {
    const target = p.widget === WIDGET_TYPE.IMAGE_UPLOAD ? uiValues : ruleValues;
    target[p.key] = values?.[p.key];
  });
  return { ruleValues, uiValues };
}

// ---------------------------- 各步骤默认值初始化 ----------------------------

/**
 * 按模板 uiSchema 的 default 构建第2步参数初值
 * 模板切换时必须调用，保证任意时刻提交都有完整参数集
 */
export function buildDefaultRuleParams(template) {
  const values = {};
  (template?.uiSchema?.params || []).forEach((p) => {
    values[p.key] = p.default;
  });
  return values;
}

/**
 * 向导单一状态源的完整初始值（新建 / 重置 时调用）
 * 结构分区与提交 DTO 拆解一一对应：base -> 主表基础字段，ruleParams -> ruleConfig/uiConfig，
 * limit -> limitType/limitCount，prizeLadders -> prizeMappingList，audience/display -> 主表展示字段
 */
export function buildDefaultWizardForm() {
  return {
    // 第1步：模板与基础信息
    // 模板由 /taskTemplate/optionList 异步下发，此处留空，向导拉到列表后自动选中第一个
    base: {
      templateCode: undefined,
      activityCode: undefined,
      taskName: '',
      triggerEvent: undefined,
      taskGroup: TASK_GROUP_ENUM.NEWBIE,
    },
    // 第2步：ui_schema 参数值（key -> value），随模板选中后按 schema 默认值重建
    ruleParams: {},
    // 第2步：参与频次
    limit: {
      limitType: LIMIT_TYPE_ENUM.ONCE,
      limitCount: DEFAULT_LIMIT_COUNT,
    },
    // 第3步：奖励阶梯（子表）
    prizeLadders: [{ ...DEFAULT_PRIZE_LADDER }],
    // 第4步：受众与时间
    audience: {
      targetAudience: TARGET_AUDIENCE_ENUM.ALL,
      timeRange: [],
      longTerm: false,
      sortWeight: DEFAULT_SORT_WEIGHT,
      actionUrl: '',
      badge: '',
    },
    // 第4步：C端展示与规则说明
    display: {
      taskDesc: '',
      ruleDesc: '',
    },
  };
}
