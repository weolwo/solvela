import { request } from './http'

/**
 * 「我的」页一屏要的全部数字（后端 MeController）。
 *
 * 🔴 每个字段都可能是 null：那一项的下游这次没取到。**null 不是 0** ——
 * 页面上显示「—」，入口照样在。把 null 当 0 显示，等于在不知道的时候告诉用户「你没有」。
 *
 * 数字由各自的域用 COUNT 口径给，**不要**再拿列表接口的结果去数：
 * 实物单、彩票的列表有条数上限，数出来的是「最近 N 条里有几张」。
 */

/** 实物单按「要不要我动手」分组。口径由资产域给，前端不按 status 推 */
export interface DeliverySummary {
  total: number
  /** 待填收货信息：要用户动手，不动手就收不到东西 */
  needAddress: number
  /** 地址已填，等仓库发 */
  pending: number
  /** 在路上 */
  shipped: number
}

export interface MeSummary {
  /** 消息未读（通知 + 公告，服务端已加好） */
  unread: number | null
  gradeName: string | null
  /** 可用券张数（口径同券包「可用」tab） */
  coupons: number | null
  /** 彩票号码张数（跨玩法跨期） */
  lotteryTickets: number | null
  /** 收藏的商品件数（只数仍可见的） */
  favorites: number | null
  deliveries: DeliverySummary | null
}

export function fetchMeSummary(): Promise<MeSummary> {
  return request<MeSummary>({ url: '/me/summary' })
}
