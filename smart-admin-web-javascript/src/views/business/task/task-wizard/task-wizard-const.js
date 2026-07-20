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

// ---------------------------- 活动大类（t_task_config.activity_code，实际应由活动接口下发） ----------------------------

export const ACTIVITY_OPTIONS = [
  { value: 'NEWBIE_CAMP', label: '🌱 新人成长营（NEWBIE_CAMP）' },
  { value: 'NATIONAL_DAY_2026', label: '🎁 国庆狂欢挑战（NATIONAL_DAY_2026）' },
  { value: 'D11_MAIN_2026', label: '🛒 双十一主会场（D11_MAIN_2026）' },
];

// ---------------------------- 触发事件 ----------------------------

export const TRIGGER_EVENT_OPTIONS = [
  { value: 'DAILY_SIGN', label: 'DAILY_SIGN（签到）' },
  { value: 'ORDER_PAID', label: 'ORDER_PAID（支付成功）' },
  { value: 'MEMBER_REGISTER', label: 'MEMBER_REGISTER（注册）' },
  { value: 'PAGE_VIEW', label: 'PAGE_VIEW（浏览页面）' },
  { value: 'CUSTOM', label: 'CUSTOM（自定义埋点）' },
];

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

// ---------------------------- 任务模板（t_task_template mock，实际应由模板接口下发） ----------------------------
// uiSchema.params 驱动第2步动态表单；widget=image_upload 的参数值提交时归入 uiConfig，其余归入 ruleConfig

export const TASK_TEMPLATES = [
  {
    templateCode: 'DAILY_SIGN_TPL',
    templateName: '每日签到',
    taskType: TASK_TYPE_ENUM.COUNT,
    icon: '📅',
    desc: '连续/累计签到达标，支持补签联动配置',
    uiSchema: {
      version: 1,
      params: [
        { key: 'targetDays', label: '连续签到目标', widget: WIDGET_TYPE.NUMBER, unit: '天', default: 7, min: 1, required: true },
        { key: 'allowRepair', label: '允许补签', widget: WIDGET_TYPE.SWITCH, default: true },
        { key: 'repairCost', label: '补签消耗积分', widget: WIDGET_TYPE.NUMBER, unit: '积分', default: 20, min: 0,
          visibleWhen: { field: 'allowRepair', eq: true }, help: '关闭「允许补签」后此项自动隐藏' },
        { key: 'customImage', label: '自定义任务图片', widget: WIDGET_TYPE.SWITCH, default: false,
          help: '开启后才显示下方图片上传项' },
        { key: 'icon_app', label: 'App端任务图标 (120x120)', widget: WIDGET_TYPE.IMAGE_UPLOAD, required: false,
          visibleWhen: { field: 'customImage', eq: true } },
        { key: 'banner_mp', label: '小程序横幅 Banner (750x300)', widget: WIDGET_TYPE.IMAGE_UPLOAD, required: true,
          visibleWhen: { field: 'customImage', eq: true } },
      ],
    },
  },
  {
    templateCode: 'INVITE_TPL',
    templateName: '邀请好友',
    taskType: TASK_TYPE_ENUM.COUNT,
    icon: '👥',
    desc: '累计邀请好友注册次数达标',
    uiSchema: {
      version: 1,
      params: [
        { key: 'targetCount', label: '邀请人数目标', widget: WIDGET_TYPE.SLIDER, min: 1, max: 20, default: 3, unit: '人' },
        { key: 'validRule', label: '有效邀请判定', widget: WIDGET_TYPE.SELECT, default: 'REAL_NAME',
          options: [
            { value: 'REGISTER', label: '注册即算' },
            { value: 'REAL_NAME', label: '注册且实名' },
            { value: 'FIRST_ORDER', label: '注册且完成首单' },
          ] },
        { key: 'icon_app', label: 'App端任务图标 (120x120)', widget: WIDGET_TYPE.IMAGE_UPLOAD, required: false },
        { key: 'banner_mp', label: '小程序横幅 Banner (750x300)', widget: WIDGET_TYPE.IMAGE_UPLOAD, required: true },
      ],
    },
  },
  {
    templateCode: 'CONSUME_TPL',
    templateName: '累计消费',
    taskType: TASK_TYPE_ENUM.AMOUNT,
    icon: '💰',
    desc: '统计周期内订单实付金额累计达标',
    uiSchema: {
      version: 1,
      params: [
        { key: 'targetAmount', label: '累计消费目标', widget: WIDGET_TYPE.NUMBER, unit: '元', default: 199, min: 1, required: true },
        { key: 'caliber', label: '统计口径', widget: WIDGET_TYPE.RADIO, default: 'PAID',
          options: [
            { value: 'PAID', label: '实付金额' },
            { value: 'ORIGIN', label: '原价金额' },
          ] },
        { key: 'excludeRefund', label: '剔除退款订单', widget: WIDGET_TYPE.SWITCH, default: true },
      ],
    },
  },
  {
    templateCode: 'BROWSE_TPL',
    templateName: '浏览页面',
    taskType: TASK_TYPE_ENUM.SIMPLE,
    icon: '👁️',
    desc: '完成一次页面浏览并满足停留时长',
    uiSchema: {
      version: 1,
      params: [
        { key: 'pageUrl', label: '目标页面路径', widget: WIDGET_TYPE.TEXT, default: '/pages/activity/qixi', required: true },
        { key: 'staySeconds', label: '停留时长要求', widget: WIDGET_TYPE.SLIDER, min: 0, max: 60, default: 10, unit: '秒' },
      ],
    },
  },
];

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
  const defaultTemplate = TASK_TEMPLATES[0];
  return {
    // 第1步：模板与基础信息
    base: {
      templateCode: defaultTemplate.templateCode,
      activityCode: undefined,
      taskName: '',
      triggerEvent: undefined,
      taskGroup: TASK_GROUP_ENUM.NEWBIE,
    },
    // 第2步：ui_schema 参数值（key -> value）
    ruleParams: buildDefaultRuleParams(defaultTemplate),
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
