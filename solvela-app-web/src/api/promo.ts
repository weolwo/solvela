import { request } from './http'

/**
 * 活动列表 / 运营焦点位。对应 <b>marketing 域</b>，2026-09-05 已接通真实接口。
 *
 * <h3>首页焦点位与活动中心是<b>同一个接口</b></h3>
 * 「精选前几条」是页面的取舍，不是第二个接口 —— 见 {@link fetchBanners}。
 * 真到了运营需要单独配「首页轮播位」的那天（那意味着一张新表），
 * 这两个函数才会分开，而不是现在先猜一个形状出来。
 */

/** 一个对 C 端可见的活动 */
export interface PromoItem {
  activityCode: string
  activityName: string
  subTitle: string | null
  themeColor: string | null
  /** 开始时间，`yyyy-MM-dd HH:mm:ss`，无时区。给「即将开始」那类文案用 */
  startTime: string
  endTime: string
  /**
   * 主图 file_id。
   * ⚠️ 网关还没暴露文件下载接口，所以现在拿到它也用不了 —— 页面继续画占位。
   * 如实接住是为了那天接通时前端不用改契约。
   */
  mainImageId: string | null
  /**
   * 此刻能不能参与。<b>由服务端时钟算好下发</b>，前端不拿本地时间自己判 ——
   * 改一下系统时间就能点开一个没开始的活动。
   *
   * <p>后端刻意<b>没有</b>下发内部的 status 枚举：那是域的状态机，
   * 前端做映射表就是第二份状态机，而且域里加一个状态时它会静默变错。
   */
  joinable: boolean
}

/**
 * 当前可见的活动。<b>包含未开始的</b> —— 页面把它们显示成「未开始」，
 * 用户看到预告才会记着回来。已下线和已结束的后端不会给。
 *
 * <p>匿名可访问：首页焦点位是所有人的入口。
 */
export function fetchPromos(): Promise<PromoItem[]> {
  return request<PromoItem[]>({ url: '/activity' })
}

/**
 * 首页顶部的运营焦点位，取前几条。
 *
 * <p>它是 {@link fetchPromos} 的一个<b>视图</b>，不是另一个接口：
 * 顺序由服务端定（进行中的排在未开始的前面），前端只截取。
 * 真有了「轮播位配置」那张表之后，这里才会变成独立请求。
 */
export async function fetchBanners(): Promise<PromoItem[]> {
  const promos = await fetchPromos()
  return promos.slice(0, 3)
}
