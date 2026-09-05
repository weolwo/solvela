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
 * 抽奖结果只回 `prizeCode`，
 * 前端拿它和这份配置对起来，才知道该落在哪一格、该显示什么名字。
 *
 * <p>🔴 奖品盘面由服务端从奖池下发，<b>不再解析 extraConfig</b> ——
 * 一个配坏的 extraConfig 不该让整个活动页崩掉。
 */

/** 转盘最多画几格。再多扇区就窄到看不清字了 */
const MAX_WHEEL_SLOTS = 12

/** 视觉分级：grand=大奖那一格（描金），normal=普通格 */
export type WheelTone = 'grand' | 'normal'

/** 转盘上的一格 */
export interface WheelPrize {
  /**
   * 与抽奖结果 {@link DrawRecord.prizeCode} 对应的奖品编码。
   *
   * 🔴 <b>「谢谢参与」也有真实编码</b>：域里它是 MARKER 类型的正常奖品，
   * 不是「没有奖品」。为 null 只出现在后端回了一个盘面上没有的编码时（兜底）。
   */
  prizeCode: string | null
  /** 格子上显示的名字 */
  label: string
  tone: WheelTone
  /** 是不是「谢谢参与」那一格。由服务端判定 —— 前端不认识 PrizeTypeEnum */
  thanks: boolean
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

/** 网关 `ActivityView` 的原样形状。只列前端用得到的字段 */
interface RawActivityView {
  activityCode: string
  activityName: string
  subTitle: string | null
  themeColor: string | null
  endTime: string
  ruleContent: string | null
  joinable: boolean
  prizes: RawActivityPrize[]
}

/** 网关 `ActivityPrizeItem`：只有这四样出公网 */
interface RawActivityPrize {
  prizeCode: string
  prizeName: string
  thanks: boolean
  featured: boolean
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
    wheel: toWheel(view.prizes),
  }
}

/**
 * 奖品列表 → 转盘。
 *
 * <h3>🔴 奖品来自服务端的奖池，不再解析 extraConfig</h3>
 * 此前转盘是从 {@code extraConfig} 里一段<b>运营手写的 JSON</b> 解析的，
 * 而抽奖引擎抽的是奖池表 —— 两个源。后果都发生过：
 * 没写就是空盘（活动页点进去什么都没有，2026-09-05 的现场），
 * 写了但对不上就是「转出一个奖池里没有的奖」。
 *
 * <p>不足 2 格返回 null（页面显示「活动配置异常」）：一个只有一格的转盘
 * 转起来没有任何意义，而那说明奖池确实没配好。
 */
function toWheel(prizes: RawActivityPrize[]): WheelConfig | null {
  // 一格转不起来；上限挡住「格子多到画不下」（原 parseWheel 里就有这两条）
  if (prizes.length < 2 || prizes.length > MAX_WHEEL_SLOTS) {
    return null
  }
  return {
    prizes: prizes.map((p) => ({
      prizeCode: p.prizeCode,
      label: p.prizeName,
      tone: p.featured ? 'grand' : 'normal',
      thanks: p.thanks,
    })),
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
  // 兜底：后端回了一个盘面上没有的编码。停在「谢谢参与」那格比停在大奖上安全得多
  const thanks = prizes.findIndex((p) => p.thanks)
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
