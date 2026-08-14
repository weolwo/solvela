/**
 * 任务配置向导 常量与枚举（消除魔法值的唯一出处）
 *
 * 其中「描述数据库列取值」的那几本字典（任务类型/分组/频次/人群）已收到 constants 目录，
 * 本文件转出即可 —— 它们不是向导独有的，列表页与表单页配的是同一批列。
 *
 * @Author:    alaric
 * @Date:      2026-07-19
 */

import { TASK_TYPE_ENUM, TASK_TYPE_OPTIONS } from '/@/constants/business/task/task-template/task-template-const';
import { PRIZE_MODE_ENUM, PRIZE_MODE_OPTIONS } from '/@/constants/business/prize/task-prize-mapping/task-prize-mapping-const';
import {
  TASK_GROUP_ENUM,
  TASK_GROUP_OPTIONS,
  LIMIT_TYPE_ENUM,
  LIMIT_TYPE_OPTIONS,
  TARGET_AUDIENCE_ENUM,
  TARGET_AUDIENCE_OPTIONS,
} from '/@/constants/business/task/task-config/task-config-const';

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

// 向导与「任务模板设计」配的是同一张 t_task_template，字典抄两份迟早会漂 —— 从常量目录转出。
// 用 import + export 而不是 `export ... from`：本文件下方的默认值要用到这些绑定，
// 而 `export ... from` 只做转发、不会在本模块作用域里建立绑定。
export { TASK_TYPE_ENUM, TASK_TYPE_OPTIONS };

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

// ---------------------------- 任务分组 / 参与频次 / 目标人群 ----------------------------

// 同上：这三本字典描述的是 t_task_config 的列，向导和列表页共用，唯一出处放在常量目录
export { TASK_GROUP_ENUM, TASK_GROUP_OPTIONS, LIMIT_TYPE_ENUM, LIMIT_TYPE_OPTIONS, TARGET_AUDIENCE_ENUM, TARGET_AUDIENCE_OPTIONS };

export const DEFAULT_LIMIT_COUNT = 1;

// ---------------------------- 奖励阶梯 ----------------------------

// 同上：向导的奖励阶梯写的就是 t_task_prize_mapping，字典唯一出处放在常量目录
export { PRIZE_MODE_ENUM, PRIZE_MODE_OPTIONS };

// ⚠️ 这里原本有一份写死的 PRIZE_CODE_OPTIONS（SCORE_100 / COUPON_100_20 …），
// 已删除：奖品编码是 t_prize_config 里的数据且**按活动隔离**，写死的四个占位值
// 在任何真实活动下都不存在。现由向导按 activityCode 调 prizeConfigApi.optionList 下发。

export const DEFAULT_PRIZE_LADDER = {
  stageCondition: 1,
  // 刻意留空：新增一级阶梯时不预填奖品，逼运营从下拉里挑一个真实存在的。
  // 之前默认填 'SCORE_100'，运营不改就能一路点到提交，建出必然发不出奖的任务。
  prizeCode: undefined,
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
