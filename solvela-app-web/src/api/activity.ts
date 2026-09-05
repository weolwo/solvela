import { type DateTimeString, toDateTime } from '@/types/contract'

import { request } from './http'

/**
 * 活动专题页 / 抽奖的数据接口。2026-09-05 已接通真实接口。
 *
 * <h3>后端一直都有，之前写桩是因为本地没活动可连</h3>
 * 网关 solvela-app 的 `ActivityController` 早就暴露了：
 * <ul>
 *   <li>`GET  /activity/{activityCode}` —— 活动详情，<b>匿名可看</b>（分享入口）；</li>
 *   <li>`POST /activity/{activityCode}/draw` —— 抽一次或连抽，<b>要登录</b>。</li>
 * </ul>
 *
 * <h3>转盘奖品从哪来</h3>
 * 后端的 `ActivityView` 里<b>没有</b>奖品列表 —— 它只有 `extraConfig`（扩展配置 JSON 原文，
 * 契约注明「前端自己解析」）。所以「转盘上有哪几格」是运营配在 extraConfig 里的，
 * 由 {@link parseWheel} 解析成 {@link WheelConfig}。抽奖结果只回 `prizeCode`，
 * 前端拿它和这份配置对起来，才知道该落在哪一格、该显示什么名字。
 *
 * <p>🔴 {@link parseWheel} <b>任何解析失败都返回 null，绝不抛</b> ——
 * 一个配坏的 extraConfig 不该让整个活动页崩掉。
 */

/** 视觉分级：grand=大奖那一格（描金），normal=普通格 */
export type WheelTone = 'grand' | 'normal'

/** 转盘上的一格 */
export interface WheelPrize {
  /**
   * 与抽奖结果 {@link DrawRecord.prizeCode} 对应的奖品编码。
   * 为 null 表示「未中奖」那一格 —— 未中奖时后端回的 prizeCode 也是 null。
   */
  prizeCode: string | null
  /** 格子上显示的名字 */
  label: string
  tone: WheelTone
}

export interface WheelConfig {
  /** 顺时针排列，索引即扇区顺序。至少 2 格 */
  prizes: WheelPrize[]
}

/** C 端看到的活动详情。对应网关的 {@code ActivityView}，内部字段不下发 */
export interface ActivityDetail {
  activityCode: string
  activityName: string
  subTitle: string | null
  /** 主题色（十六进制），没配为 null */
  themeColor: string | null
  endTime: DateTimeString
  /** 规则正文（富文本 HTML），没配为 null */
  ruleContent: string | null
  /**
   * 此刻能不能参与。<b>由服务端时钟算好下发</b> —— 不让客户端拿本地时间自己判。
   * 按钮要不要置灰看它。
   */
  joinable: boolean
  /**
   * extraConfig 解析出来的转盘配置。
   * 非转盘玩法、没配、或配坏了都是 null —— 这时专题页显示「活动配置异常」而不是白屏。
   */
  wheel: WheelConfig | null
}

/** 抽奖结果里的一次。对应网关 {@code DrawView.DrawItemView} */
export interface DrawRecord {
  hit: boolean
  /** 中奖奖品编码；未中奖为 null */
  prizeCode: string | null
}

/** 一批抽奖的结果。对应网关的 {@code DrawView}。单抽就是 records 只有一条 */
export interface DrawOutcome {
  /** 每一次的结果，顺序即抽奖顺序 */
  records: DrawRecord[]
  /** 中了几个 */
  hitCount: number
  /** 给用户看的一句话，由网关那一层决定措辞（域只陈述发生了什么） */
  message: string
}

/**
 * 一次请求最多抽几次。<b>对齐后端 {@code DrawLimits.MAX_TIMES}</b> —— 那是契约里的一个数，
 * 网关用 {@code @Max} 卡在入口。前端这里同一个值，是为了「十连抽」按钮别传一个会被 400 的数。
 */
export const MAX_DRAW_TIMES = 10

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/** Array.isArray 会把入参窄成 any[]，套一层守卫窄成 unknown[]，下游才不吃到 any */
function isUnknownArray(value: unknown): value is unknown[] {
  return Array.isArray(value)
}

function toWheelPrize(value: unknown): WheelPrize | null {
  if (!isRecord(value)) {
    return null
  }
  const { prizeCode, label, tone } = value
  if (typeof label !== 'string' || label === '') {
    return null
  }
  if (prizeCode !== null && typeof prizeCode !== 'string') {
    return null
  }
  return {
    prizeCode: prizeCode ?? null,
    label,
    // 认不出的分级一律当普通格，不因为多了个新值就整份配置作废
    tone: tone === 'grand' ? 'grand' : 'normal',
  }
}

/**
 * 把 {@code extraConfig} 原文解析成转盘配置。
 *
 * 🔴 任何解析失败（不是 JSON、结构不对、格子不够两个）都返回 null，<b>绝不抛</b> ——
 * 一个配坏的 extraConfig 不该让整个活动页崩掉。null 由页面翻成「活动配置异常，请稍后再来」。
 */
export function parseWheel(extraConfig: string | null): WheelConfig | null {
  if (extraConfig === null || extraConfig.trim() === '') {
    return null
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(extraConfig) as unknown
  } catch {
    return null
  }
  if (!isRecord(parsed) || !isRecord(parsed.wheel) || !isUnknownArray(parsed.wheel.prizes)) {
    return null
  }
  const prizes: WheelPrize[] = []
  for (const raw of parsed.wheel.prizes) {
    const prize = toWheelPrize(raw)
    if (prize === null) {
      return null
    }
    prizes.push(prize)
  }
  // 一格转不起来；这里也顺手挡住「格子多到画不下」
  if (prizes.length < 2 || prizes.length > 12) {
    return null
  }
  return { prizes }
}

/** 网关 `ActivityView` 的原样形状。只列前端用得到的字段 */
interface RawActivityView {
  activityCode: string
  activityName: string
  subTitle: string | null
  themeColor: string | null
  endTime: string
  extraConfig: string | null
  ruleContent: string | null
  joinable: boolean
}

/**
 * 活动详情。活动不存在 → 后端回 404，这里抛 ApiError(NOT_FOUND)。
 *
 * <p>匿名可访问：活动页是分享出去的入口。
 */
export async function fetchActivityDetail(activityCode: string): Promise<ActivityDetail> {
  const view = await request<RawActivityView>({ url: `/activity/${activityCode}` })
  return {
    activityCode: view.activityCode,
    activityName: view.activityName,
    subTitle: view.subTitle,
    themeColor: view.themeColor,
    endTime: toDateTime(view.endTime),
    ruleContent: view.ruleContent,
    // joinable 由服务端时钟算好下发，不让客户端拿本地时间自己判
    joinable: view.joinable,
    wheel: parseWheel(view.extraConfig),
  }
}

/**
 * 抽一次或连抽。
 *
 * @param times     抽几次。只是<b>意愿</b>（用户点的是「单抽」还是「十连」）——
 *                  真正抽几次由营销侧的编排脚本结合剩余次数裁决，看返回的 records 长度。
 *                  上限 {@link MAX_DRAW_TIMES}，后端用 `@Max` 卡在入口。
 * @param requestId 幂等键，<b>客户端生成，一次点击一个</b>。网络超时不代表没抽 ——
 *                  没有它就只能在「可能重复发奖」和「可能白扣一次机会」之间选一个。
 *
 * <p>没被受理（活动没开、限流、奖池不可用）时后端回 4xx，这里抛 ApiError，
 * 调用方按 message 提示即可，<b>那几种都是预期内的</b>。
 */
export function drawActivity(
  activityCode: string,
  times: number,
  requestId: string,
): Promise<DrawOutcome> {
  return request<DrawOutcome>({
    url: `/activity/${activityCode}/draw`,
    method: 'POST',
    data: { requestId, times },
  })
}

/**
 * 帮页面把抽奖结果对到转盘的某一格上：单抽落中奖那格，连抽落<b>第一个中的</b>那格
 *（都没中就落未中奖那一格）。配置漂移导致编码对不上时落第 0 格，不让它算出 -1。
 */
export function landingIndex(outcome: DrawOutcome, prizes: WheelPrize[]): number {
  const target = outcome.records.find((r) => r.hit) ?? outcome.records[0] ?? null
  const code = target?.prizeCode ?? null
  const exact = prizes.findIndex((p) => p.prizeCode === code)
  if (exact >= 0) {
    return exact
  }
  const thanks = prizes.findIndex((p) => p.prizeCode === null)
  return thanks >= 0 ? thanks : 0
}

/** 把一个 prizeCode 显示成名字。认不出就退回「下回好运」（null）或编码本身 */
export function prizeLabel(code: string | null, prizes: WheelPrize[]): string {
  const found = prizes.find((p) => p.prizeCode === code)
  if (found !== undefined) {
    return found.label
  }
  return code ?? '下回好运'
}
