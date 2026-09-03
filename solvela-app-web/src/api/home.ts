import { type Id, toId } from '@/types/contract'
import type { CarouselItem } from '@/ui/SvCarousel.vue'

/**
 * 首页与优惠页的数据接口。
 *
 * <h3>🔴 后端还没有这三个接口，本文件目前是桩</h3>
 * 网关 solvela-app 现在只暴露了 `/auth/*` 与 `/activity/{code}`、`/activity/{code}/draw`。
 * 也就是说：
 * <ul>
 *   <li><b>资产</b>没有接口 —— 余额在 ledger 域，还没接到网关；</li>
 *   <li><b>订单/奖品记录</b>没有接口 —— prize 域的 t_prize_log 没开 C 端查询；</li>
 *   <li><b>活动列表</b>没有接口 —— ActivityApi 只能<b>按 code 查单个</b>，
 *       没有「列出当前开启的活动」；</li>
 *   <li><b>首页轮播</b>没有接口，也没有「轮播图配置」这张表 —— 运营还没有地方去配
 *       一张 banner 图。现在直接复用 {@link fetchPromos} 的前几条渲染成焦点位
 *       （标题、副标题、主题色），不是另开一份数据。等真的有轮播配置接口时，
 *       这两个函数才会分开：一个是精选排序，一个是完整列表。</li>
 * </ul>
 *
 * <p>所以下面每个函数都返回写死的样例数据，<b>但形状就是接口该有的形状</b>：
 * 后端接上之后，改的只是函数体里那一句 request，页面一行不用动。
 *
 * <p>⚠️ 桩数据必须一眼看得出是假的（编号带 DEMO、名字带「样例」），
 * 否则联调时会有人以为链路通了。
 */

/** 一种资产。种类由后端给，前端不硬编码「积分/抽奖次数」这类枚举 */
export interface AssetItem {
  /** 资产类型编码，如 SCORE / TICKET */
  assetType: string
  label: string
  /** 金额永远是字符串，见 types/contract。不要 Number() 之后 toFixed */
  amount: string
  /** 是否按金额展示（带千分位与小数）。次数类资产用整数展示 */
  currency: boolean
}

/** 一条奖品/订单记录 */
export interface RecordItem {
  id: Id
  title: string
  /** 展示用的状态文案，由后端给 —— 前端做映射表就是第二份状态机 */
  statusText: string
  /** PENDING / DONE / FAILED，只用于选颜色，不直接展示 */
  status: 'PENDING' | 'DONE' | 'FAILED'
  createTime: string
  amount: string | null
}

/** 一个进行中的活动 */
export interface PromoItem {
  activityCode: string
  activityName: string
  subTitle: string | null
  themeColor: string | null
  endTime: string
  /** 此刻能不能参与。由服务端时钟算好下发，不让客户端自己判 */
  joinable: boolean
}

/** 桩：假装网络往返一下，让加载态在开发时真的会出现 */
function stub<T>(data: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), 300))
}

/**
 * 首页顶部的运营焦点位，取当前活动的前几条。
 *
 * TODO 后端补「轮播图配置」接口后改成独立请求；在那之前它是
 * {@link fetchPromos} 的一个视图，不是重复的桩数据源 —— 只有一份假数据，
 * 不会出现两处桩各改各的、渐渐对不上的问题。
 */
export async function fetchBanners(): Promise<CarouselItem[]> {
  const promos = await fetchPromos()
  return promos.slice(0, 3).map((p) => ({
    id: p.activityCode,
    title: p.activityName,
    subtitle: p.subTitle,
    themeColor: p.themeColor,
  }))
}

/** TODO 后端补 GET /assets 后改成 request({ url: '/assets' }) */
export function fetchAssets(): Promise<AssetItem[]> {
  return stub([
    { assetType: 'SCORE', label: '积分', amount: '12345.67', currency: true },
    { assetType: 'TICKET', label: '抽奖次数', amount: '3', currency: false },
  ])
}

/** TODO 后端补 GET /records 后改成 request({ url: '/records' }) */
export function fetchRecords(): Promise<RecordItem[]> {
  return stub([
    {
      id: toId('9001'),
      title: '样例·一等奖 iPhone 17',
      statusText: '已发放',
      status: 'DONE',
      createTime: '2026-09-01 20:31:05',
      amount: null,
    },
    {
      id: toId('9002'),
      title: '样例·积分 100',
      statusText: '发放中',
      status: 'PENDING',
      createTime: '2026-09-01 20:30:58',
      amount: '100',
    },
  ])
}

/**
 * TODO 后端补 GET /activity（当前开启的活动列表）后改成真实请求。
 *
 * ⚠️ 这个接口<b>后端一行都还没有</b>：ActivityApi 只有 getActivityRule(code)。
 * 要做的话是 marketing 侧加一个 listOpenActivities，网关加一个 GET /activity。
 */
export function fetchPromos(): Promise<PromoItem[]> {
  return stub([
    {
      activityCode: 'DEMO-MIDAUTUMN',
      activityName: '样例·中秋抽好礼',
      subTitle: '每天 3 次机会，抽 iPhone',
      themeColor: null,
      endTime: '2026-09-30 23:59:59',
      joinable: true,
    },
    {
      activityCode: 'DEMO-NEWCOMER',
      activityName: '样例·新人专享',
      subTitle: '注册即得 100 积分',
      themeColor: null,
      endTime: '2026-12-31 23:59:59',
      joinable: false,
    },
  ])
}
