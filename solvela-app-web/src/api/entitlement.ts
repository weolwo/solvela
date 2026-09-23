import { request } from './http'

/**
 * 我的等级权益：生日礼、月度券。
 *
 * <h3>为什么是「待领取」而不是自动到账</h3>
 * 自动发的话用户无感，而券是有成本的——占预算、进报表，最后大量躺着没人用。
 * 让用户来点一下，「未领取」本身就成了一个召回理由，
 * 也不会把预算花在不活跃的人身上。
 *
 * <h3>🔴 全是我自己的，memberId 由网关从登录态取</h3>
 * 契约里一个 memberId 参数都没有——接受客户端传的话，
 * 列表那边是「查任意人有什么权益」，领取那边更糟：**改个参数就能领走别人的东西**。
 */

/** 0-待领取, 1-已领取, 2-已过期 */
export type EntitlementStatus = 0 | 1 | 2

export interface MemberEntitlement {
  grantId: number
  /**
   * 权益名，如「白金生日礼」。
   *
   * ⚠️ 与 `assetName` 是两件事：这个是**为什么给你**，那个是**给你什么**。
   * 页面上两件都要说——合并成一个的话只能二选一，而用户两件都想知道。
   */
  entitlementName: string
  /** 领到的东西叫什么，如「生日 20 元券」 */
  assetName: string
  /** 周期键。生日礼是 `yyyy`，月度券是 `yyyyMM` */
  periodKey: string
  status: EntitlementStatus
  /** 领取截止时间。已领取的也给——用户会想知道当时的期限 */
  expireTime: string
  /** 领取时间，没领为 null */
  claimTime: string | null
}

/** 我的权益，待领取在前、快过期的靠前 */
export function fetchMyEntitlements(): Promise<MemberEntitlement[]> {
  return request<MemberEntitlement[]>({ url: '/entitlement' })
}

/** 领取结果 */
export interface EntitlementClaimResult {
  /** 领到的东西叫什么，如「生日 20 元券」 */
  assetName: string
}

/**
 * 领取一份。
 *
 * ⚠️ 领不了时后端抛业务异常（已领过 / 过期了 / 不是你的），不是返回一个「失败」的成功响应。
 * 所以调用方只需要 try/catch，不必自己判状态。
 *
 * 🔴 返回的是对象不是裸字符串：裸 `String` 会被后端序列化成 `text/plain`，
 * 而网关的 RestClient 没有对应的转换器——单进程内正常，跨进程直接炸。
 * 2026-09-23 真机上撞过一次。
 */
export function claimEntitlement(grantId: number): Promise<EntitlementClaimResult> {
  return request<EntitlementClaimResult>({ url: `/entitlement/${grantId}/claim`, method: 'POST' })
}
