/*
 * 大屏配色与图表底子（三个图表组件共用）
 *
 * ⚠️ 不要在组件里再写死 hex。之前三个图表各挑各的颜色，同一份数据在两张图里
 *    是两种色系，看着就不像一个系统里的东西。
 *
 * ══ 分色规则（四种职责，别混用）══
 *   SERIES  分类：只表示「是谁」，按固定槽位顺序取，**颜色跟着实体走，不跟着排名走**
 *           —— 否则筛掉一个系列，剩下的会集体换色，读图的人以为数据变了。
 *   STATUS  状态：好/警告/危险，**保留给状态**，绝不拿来当第四个系列色。
 *   RAMP    有序：同一个蓝色由浅到深，用于漏斗这种「一个量的分档」。
 *   CHROME  图表框架：网格线、轴、文字，一律比数据弱一个层级。
 *
 * ══ 这套槽位顺序是验算过的，不是挑好看的 ══
 * 在白色卡片底（#ffffff）上跑过配色校验：
 *   3 槽（蓝/橙/青）：相邻色盲 ΔE 9.2、常视 27.6 —— 全项通过
 *   4 槽（+黄）    ：相邻色盲 ΔE 9.1、常视 22.9 —— 全项通过
 * ⚠️ 但**只在「相邻配对」口径下成立**（堆叠柱、折线就是这个口径）。
 *    全配对口径下 黄↔橙 常视 ΔE 13.7 不达标 —— 所以：
 *    ① 堆叠顺序必须按槽位顺序来，不能按数值大小排；
 *    ② 散点/气泡这类「任意两色都会挨在一起」的图，最多用前三槽。
 * ⚠️ 青(2.82:1) 与 黄(2.17:1) 对白底不足 3:1 —— 用到它们的图必须另给一条读数通道
 *    （直接标注或旁边的数字明细），不能只靠颜色。
 */

/** 分类槽位：蓝 → 橙 → 青 → 黄。新增系列往后取，不要插队、不要循环复用 */
export const SERIES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100'];

/** 状态色：固定语义，配合文字/图标使用，绝不单独靠颜色表意 */
export const STATUS = {
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b',
  neutral: '#898781',
};

/**
 * 有序蓝色梯（漏斗按档位由浅到深）。
 * ⚠️ 最浅一档不能比 #86b6ef 更浅：再浅就贴到白底上了（对比度低于 2:1）。
 */
export const RAMP = ['#86b6ef', '#5598e7', '#2a78d6', '#1c5cab', '#104281'];

/** 图表框架：全部比数据弱一级，别抢戏 */
export const CHROME = {
  gridline: '#e1e0d9',
  axisLine: '#c3c2b7',
  textMuted: '#898781',
  textSecondary: '#52514e',
  surface: '#ffffff',
};

/**
 * 玩法类型 → 槽位（**固定映射**，不随数据里谁多谁少变化）
 */
export const ACTIVITY_TYPE_COLOR = {
  DRAW: SERIES[0],
  TASK: SERIES[1],
  LOTTERY: SERIES[2],
};

/**
 * 资产类型 → 槽位（固定映射）。
 * ⚠️ 堆叠时必须按这里的顺序码放，相邻配对才与校验结果一致。
 */
export const ASSET_ORDER = ['SCORE', 'COUPON', 'BALANCE', 'PHYSICAL', 'MARKER'];
export const ASSET_COLOR = {
  SCORE: SERIES[0],
  COUPON: SERIES[1],
  BALANCE: SERIES[2],
  PHYSICAL: SERIES[3],
  // 标记（谢谢参与）排在最后且给中性色：它是所有奖项里被抽中最频繁的一档，
  // 占一个高饱和槽位会在图上盖过真正要看的资产口径。
  // 它也没有金额可堆叠 —— prize_value 恒为 0，只在「条数」口径下有意义。
  MARKER: STATUS.neutral,
  // 这两类目前没有派发策略，正常不会出现在图上；真出现了给个中性色，别顶掉正经槽位
  LOTTERY: STATUS.neutral,
  CUSTOM: STATUS.neutral,
};

/** 抽奖状态 → 状态色。0-未中奖 1-已中奖 2-库存不足 3-异常 */
export const DRAW_STATUS_COLOR = {
  0: STATUS.neutral,
  1: STATUS.good,
  2: STATUS.critical,
  3: STATUS.warning,
};

/** 每张图都长一样的那部分：坐标轴、网格、提示框 */
export const axisStyle = {
  axisLine: { lineStyle: { color: CHROME.axisLine } },
  axisTick: { show: false },
  axisLabel: { color: CHROME.textMuted, fontSize: 11 },
};

export const splitLineStyle = {
  // 实线细网格，不用虚线 —— 虚线是「噪点」，会跟数据抢注意力
  splitLine: { lineStyle: { color: CHROME.gridline, width: 1, type: 'solid' } },
};

export const tooltipStyle = {
  backgroundColor: 'rgba(255,255,255,0.98)',
  borderColor: CHROME.gridline,
  borderWidth: 1,
  padding: [8, 12],
  textStyle: { color: '#0b0b0b', fontSize: 12 },
  extraCssText: 'box-shadow: 0 6px 24px rgba(11,11,11,0.10); border-radius: 8px;',
};

export const legendStyle = {
  top: 0,
  itemWidth: 10,
  itemHeight: 10,
  itemGap: 16,
  icon: 'roundRect',
  textStyle: { color: CHROME.textSecondary, fontSize: 12 },
};
