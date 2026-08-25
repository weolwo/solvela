/**
 * job 常量
 */

/**
 * 触发类型。
 *
 * 🔴 FIXED_DELAY 已删除：它与抢占式调度概念上不相容 ——
 * 「上一次执行结束后再等 N 秒」的语义要求知道任务何时结束，
 * 而抢占发生在任务开始之前。硬留下来只会变成 FIXED_RATE（语义悄悄换了）。
 * 短周期改用 cron 的秒字段表达，例如「每 10 秒」写成 星号斜杠10 空格 * * * * *
 * （这里不写原样示例：cron 里的斜杠星号会提前闭合 JS 块注释）。
 */
export const TRIGGER_TYPE_ENUM = {
  CRON: {
    value: 'cron',
    desc: 'cron表达式',
  },
  ONE_TIME: {
    value: 'one_time',
    desc: '一次性',
  },
};

/**
 * 预设档位：不让运营直面十来个旋钮。
 * 这些参数本就高度相关（轻量的必然 DISCARD、重批处理必然要补跑），
 * 用档位把相关性表达出来，比让运营自己去悟组合便宜得多。
 */
export const PRESET_ENUM = {
  LIGHT: { value: 'LIGHT', desc: '轻量高频', hint: '探活、状态流转 ｜ 超时30秒 ｜ 错过即跳过 ｜ 不重试', lane: 'FAST' },
  NORMAL: { value: 'NORMAL', desc: '常规', hint: '清理、扫描 ｜ 超时5分钟 ｜ 错过即跳过 ｜ 重试1次', lane: 'SLOW' },
  HEAVY: { value: 'HEAVY', desc: '重批处理', hint: '统计、对账 ｜ 超时30分钟 ｜ 错过则补跑 ｜ 重试2次', lane: 'SLOW' },
  CUSTOM: { value: 'CUSTOM', desc: '自定义', hint: '逐项自行设置', lane: null },
};

export const LANE_ENUM = {
  FAST: { value: 'FAST', desc: '快车道', color: 'green' },
  SLOW: { value: 'SLOW', desc: '慢车道', color: 'blue' },
};

/**
 * 任务来源。
 *
 * SYSTEM = 系统内置或活动向导衍生 —— 在列表里可见、打标签、但就地只读：
 * 既满足排障需要的全局视图（藏起来会导致「明明有任务在跑却哪儿都找不到配置」），
 * 又避免配置入口脑裂（同一个东西两个地方能改，迟早不一致）。
 */
export const JOB_SOURCE_ENUM = {
  MANUAL: { value: 'MANUAL', desc: '人工创建' },
  SYSTEM: { value: 'SYSTEM', desc: '衍生任务' },
};

export const TRIGGER_SOURCE_ENUM = {
  SCHEDULE: { value: 'SCHEDULE', desc: '定时' },
  MANUAL: { value: 'MANUAL', desc: '手动' },
};

/**
 * 执行状态。
 *
 * 🔴 取代了原来的布尔 successFlag —— 布尔表达不了「执行中」，
 * 所以旧实现只能在开跑前先写一条「成功」，进程一崩就永久留下假的绿色记录。
 *
 * 与后端 SolvelaJobExecuteStatusEnum 一一对应，值不要改。
 * 5~7 是抢占式调度（第二档）才会写入的状态，先列出来免得届时前端漏改。
 */
export const EXECUTE_STATUS_ENUM = {
  PENDING: { value: 0, desc: '待执行', color: 'default' },
  RUNNING: { value: 1, desc: '执行中', color: 'processing' },
  SUCCESS: { value: 2, desc: '成功', color: 'success' },
  FAIL: { value: 3, desc: '失败', color: 'error' },
  TIMEOUT: { value: 4, desc: '超时中断', color: 'warning' },
  BLOCKED: { value: 5, desc: '阻塞丢弃', color: 'default' },
  MISFIRE: { value: 6, desc: '错过调度', color: 'warning' },
  INTERRUPTED: { value: 7, desc: '中断', color: 'warning' },
};

/**
 * 按 value 取状态定义。
 *
 * ⚠️ 用 Object.values 遍历比对，不要写成 EXECUTE_STATUS_ENUM[status] ——
 * 那是「枚举键 Map 用值去 get」，本项目已因此踩过三次静默失效。
 */
export function getExecuteStatus(status) {
  return Object.values(EXECUTE_STATUS_ENUM).find((e) => e.value === status);
}

export default {
  TRIGGER_TYPE_ENUM,
  EXECUTE_STATUS_ENUM,
  PRESET_ENUM,
  LANE_ENUM,
  TRIGGER_SOURCE_ENUM,
  JOB_SOURCE_ENUM,
};
