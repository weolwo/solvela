import { request } from './http'

/**
 * 资产（余额、次数）。对应 <b>ledger 域</b>，2026-09-05 已接通真实接口。
 *
 * <p>⚠️ 这里是 marketing↔ledger 那条缝的前端一侧：<b>资产的读只走本文件</b>。
 * 将来资产域独立出去（独立控制台 / 独立网关前缀）时，要改的是这一个文件的路径，
 * 不该牵动 activity / promo / mall。所以别把「抽奖送了多少积分」「兑换扣了多少」
 * 这类跨域编排写进来 —— 那是各自服务端的事，前端只分别读两边的结果。
 *
 * <h3>🔴 这里只有读，没有写</h3>
 * 加钱减钱一律由服务端在自己的事务里做（抽奖发奖、任务领取、商城兑换）。
 * 前端「先调 A 再调 B」的写法中间断网就是账不平，而且是用户看不见的那种不平。
 */

/** 一种资产。种类由后端给，前端不硬编码「积分/余额」这类枚举 */
export interface AssetItem {
  /** 资产类型编码，如 SCORE / BALANCE */
  assetType: string
  /** 给用户看的名字，由网关那一层决定措辞 */
  label: string
  /** 金额永远是字符串，见 types/contract。**不要 Number() 之后 toFixed** */
  amount: string
  /** 是否按金额展示（带千分位与小数）。次数类资产用整数展示 */
  currency: boolean
  /**
   * 钱包被冻结。
   * <p>冻结时仍然<b>展示</b>余额并标注，不要把这一项藏起来 ——
   * 藏起来用户会以为资产没了，然后来问客服。
   */
  frozen: boolean
}

/**
 * 我的全部资产。
 *
 * <p>会员号由服务端从登录态取，<b>接口上没有 memberId 参数</b>（有了就等于开放
 * 「查任意会员余额」）。所以这里也不传。
 *
 * <p>没有任何钱包记录时返回<b>空数组</b>，不是 404 —— 新注册、还没拿过奖励的
 * 用户就是这个状态，页面按空态渲染即可。
 */
export function fetchAssets(): Promise<AssetItem[]> {
  return request<AssetItem[]>({ url: '/assets' })
}
