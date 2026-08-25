/*
 * 统计面板的展示格式化
 *
 * 收在一处不是为了省这几行，而是为了口径只有一份：
 * 同一个 0.5，这个页面显示 50%、那个页面显示 50.00%，看的人会以为是两个不同的数。
 *
 * @Author:    alaric
 * @Date:      2026-08-18
 */

const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

/**
 * 计数。null 显示成「-」而不是 0 —— 「还没取到」和「真的是 0」是两回事，
 * 一律显示 0 会让接口挂了的时候页面看起来一切正常。
 */
export function num(value) {
  return value == null ? '-' : Number(value).toLocaleString();
}

/**
 * 百分比。小于 1% 时保留两位有效数字，否则 0.03% 会被四舍五入成 0.00%，
 * 而「有一点」和「一点没有」在体检类指标上是完全不同的结论。
 */
export function percent(rate) {
  if (rate == null) {
    return '-';
  }
  const v = Number(rate) * 100;
  if (v === 0) {
    return '0%';
  }
  return v >= 1 ? `${v.toFixed(2)}%` : `${v.toPrecision(2)}%`;
}

/**
 * 金额。DECIMAL(18,4) 直接显示会变成「5830.0000 积分」，多出来的两位没有意义。
 * 负数保留负号：净额为负是一个结论（这段时间用户手上的资产净减少），不是错误。
 */
export function money(value) {
  if (value == null) {
    return '0';
  }
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
}

/**
 * 条形图宽度。占比再小也给 1% 的宽度，否则条上只剩一条看不见的缝，
 * 读者会以为渲染坏了。
 */
export function barPercent(rate) {
  return Math.max(1, Math.round(Number(rate || 0) * 100));
}

/**
 * 时长。「1440 分钟」没人读得出是一天，按量级换算成小时/天。
 */
export function waitedText(minutes, hasBacklog = true) {
  if (!hasBacklog) {
    return '无积压';
  }
  const value = Number(minutes || 0);
  if (value < MINUTES_PER_HOUR) {
    return `${value} 分钟`;
  }
  if (value < MINUTES_PER_DAY) {
    return `${Math.floor(value / MINUTES_PER_HOUR)} 小时`;
  }
  return `${Math.floor(value / MINUTES_PER_DAY)} 天`;
}

/**
 * 积压的告警语气：有积压才提醒，超过一天才标红。
 * 门槛太低会天天报警，报警就没人看了。
 */
export function backlogTone(count, oldestMinutes) {
  if (!count) {
    return 'is-muted';
  }
  return Number(oldestMinutes || 0) >= MINUTES_PER_DAY ? 'is-critical' : 'is-warning';
}

export { MINUTES_PER_DAY, MINUTES_PER_HOUR };
