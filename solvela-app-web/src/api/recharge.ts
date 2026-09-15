import { request } from './http'
import type { Id } from '@/types/contract'

/**
 * 充话费。券的第一个**非商城**出口。
 *
 * <h3>⚠️ 今天运营商那一端是假的</h3>
 * 下单、扣券、标成功，**但话费不会到账**。假充值配到生产会让后端**启动失败** ——
 * 那道闸在后端，前端这一侧不做任何环境判断（判断散在两处，总有一处会忘）。
 *
 * <h3>🔴 抵扣额由服务端算，请求里没有这个字段</h3>
 * 客户端只说「用哪张券」。让它报「减多少」就是一个可以直接刷钱的口子。
 */

export interface RechargeOptions {
  /**
   * 场景开着没有。
   *
   * 🔴 关着时要**如实显示「暂未开放」**，不要把入口藏起来 ——
   * 藏起来用户会以为是自己的问题。
   */
  enabled: boolean
  sceneCode: string
  /** 可选面额。**只认这个白名单**，不接受任意金额 */
  faceValues: string[]
  /** 最低充值金额。⚠️ 它和「券的门槛」是两回事 */
  minAmount: string
}

export interface RechargeCouponItem {
  couponId: Id
  couponName: string
  /** 这一笔能减多少。不可用时为 '0' */
  discountAmount: string
  usable: boolean
  /**
   * 用不了的原因（人话）。
   *
   * 🔴 **要显示出来**，不要把不可用的券过滤掉 —— 用户手里有券却看不到它，
   * 第一反应是系统坏了，而真实原因往往只是「没到门槛」。
   */
  reasonDesc: string | null
}

export interface RechargeTrialResult {
  usable: RechargeCouponItem[]
  unusable: RechargeCouponItem[]
}

export interface RechargeOrderResult {
  accepted: boolean
  orderNo: string
  payAmount: string
  reason: string | null
}

export interface RechargeOrder {
  orderNo: string
  /** 打码后的手机号。🔴 明文不出网关 */
  targetMasked: string
  originalAmount: string
  couponDiscount: string | null
  payAmount: string
  status: number
  statusDesc: string
  /** 能不能点「去支付」。由**服务端**判，前端不按 status 自己推 */
  payable: boolean
  createTime: string
}

/** 可选面额与最低金额。场景关着时 enabled = false */
export function fetchRechargeOptions(): Promise<RechargeOptions> {
  return request<RechargeOptions>({ url: '/recharge/options' })
}

/** 选券：这一笔能用哪些券、各能减多少、用不了的为什么 */
export function trialRechargeCoupons(amount: string): Promise<RechargeTrialResult> {
  return request<RechargeTrialResult>({
    url: '/recharge/trial',
    method: 'POST',
    params: { amount },
  })
}

/**
 * 下单。
 *
 * ⚠️ 请求里**没有**「抵扣多少」，也不该有：抵扣额由服务端重新试算。
 */
export function createRechargeOrder(payload: {
  targetAccount: string
  amount: string
  couponId: Id | null
}): Promise<RechargeOrderResult> {
  return request<RechargeOrderResult>({ url: '/recharge/order', method: 'POST', data: payload })
}

/** 支付并执行。⚠️ 今天两者都是假的，所以是一个动作 */
export function payRechargeOrder(orderNo: string): Promise<RechargeOrderResult> {
  return request<RechargeOrderResult>({ url: `/recharge/order/${orderNo}/pay`, method: 'POST' })
}

/** 我的充值记录，新的在前 */
export function fetchRechargeOrders(): Promise<RechargeOrder[]> {
  return request<RechargeOrder[]>({ url: '/recharge/order' })
}
