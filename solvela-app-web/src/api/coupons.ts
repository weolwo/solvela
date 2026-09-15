import { request } from './http'
import type { Id } from '@/types/contract'

/**
 * 券包与选券。对应 <b>ledger 域</b>的只读那一半。
 *
 * <h3>🔴 这里只有读和试算，没有「用券」</h3>
 * 券什么时候被消耗掉，只能由下单那条链路决定。前端调一个「用券」接口再调下单，
 * 中间断网就是「券没了但单没下成」—— 而且是用户看不见的那种没了。
 * 服务端那一侧也刻意没有把核销接口接到网关上。
 *
 * <h3>⚠️ 试算结果不是承诺</h3>
 * 从看到「可减 1000」到点确认之间，这张券可能在另一个端上被用掉。
 * 真正作数的是下单时服务端<b>重新试算并锁定</b>的那一次 ——
 * 所以下单请求里<b>没有</b>「抵扣多少」这个字段，只有「用哪张」。
 */

/** 券包的三个 tab。和券的 status 不是一一对应：可用会排除「正被某单锁着」的 */
export type CouponTab = 'USABLE' | 'USED' | 'INVALID'

export interface MemberCoupon {
  couponId: Id
  couponName: string
  /**
   * 规则的人话版本，如「满 100 积分可用，减 20 积分」。
   *
   * 🔴 由服务端拼好，前端<b>不要自己从 discountType/discountValue 拼</b> ——
   * 那段逻辑在 C 端、管理端各存一份的话，一定会不一致，
   * 而不一致的表现是用户在两个地方看到同一张券的不同规则。
   */
  ruleText: string
  discountType: 'FIXED' | 'PERCENT' | null
  /** 金额永远是字符串，见 types/contract。**不要 Number() 之后 toFixed** */
  discountValue: string | null
  minAmount: string | null
  maxDiscount: string | null
  deductTarget: 'CASH' | 'SCORE' | null
  /** 0-未使用 1-已使用 2-已过期 3-已作废 4-锁定中 */
  status: number
  statusDesc: string
  validEndTime: string | null
  usedTime: string | null
  /** 实际抵扣了多少，没用过时为 null */
  discountAmount: string | null
}

export interface CouponTrialItem {
  couponId: Id
  couponName: string
  /** 这一单能减多少。不可用时为 '0' */
  discountAmount: string
  usable: boolean
  /** 不可用的原因码，可用时为 null */
  reason: string | null
  /**
   * 原因的人话版本。
   *
   * 🔴 <b>要显示出来</b>，不要把不可用的券过滤掉 ——
   * 用户手里有券却在下单页看不到它，第一反应是系统坏了，
   * 而真实原因往往只是「没到门槛」，那一句话能省掉一次客服。
   */
  reasonDesc: string | null
  validEndTime: string | null
  /**
   * 这张券减的是哪一半。
   *
   * 🔴 混合支付单（积分 + 现金）上，账单要按它决定把抵扣落在积分侧还是现金侧。
   * 一律当成减积分的话，用现金券的单会显示成「积分少扣了」，而实际扣的是现金。
   */
  deductTarget: 'CASH' | 'SCORE'
}

/** 同一个抵扣对象下的可用券 */
export interface CouponTrialGroup {
  deductTarget: 'CASH' | 'SCORE'
  /** 已按能减多少从大到小排好，第一张就是<b>本组</b>最优 */
  items: CouponTrialItem[]
}

/**
 * 试算结果：**按抵扣对象分组**。
 *
 * 🔴 没有「全局最优」这个字段，也不该有：一张减 10 积分的券和一张减 5 元的券，
 * 谁更划算系统答不了 —— 1 积分 ≠ 1 元，而汇率是业务定义、还会变。
 * 组内排序，跨组让用户自己挑。
 */
export interface CouponTrialResult {
  /** 这一单没有的那一侧不会出现（纯积分单就只有 SCORE 一组） */
  groups: CouponTrialGroup[]
  /** 用不了的券，每张带着原因。**要展示，别过滤** */
  unusable: CouponTrialItem[]
}

/**
 * 我的券包。
 *
 * <p>会员号由服务端从登录态取，<b>接口上没有 memberId 参数</b>。
 * 一张券都没有时返回<b>空数组</b>，不是 404 —— 新用户就是这个状态。
 */
export function fetchCoupons(status: CouponTab): Promise<MemberCoupon[]> {
  return request<MemberCoupon[]>({ url: '/coupons', params: { status } })
}

/**
 * 下单页选券：这一单能用哪些券、各能减多少。
 *
 * <p>⚠️ 请求里<b>没有「抵扣多少」</b>，也不该有：让客户端报数就是一个
 * 可以直接刷钱的口子，而且下单时服务端还会再算一次。
 */
export function trialCoupons(payload: {
  commodityId: Id
  skuId: Id
  quantity: number
}): Promise<CouponTrialResult> {
  return request<CouponTrialResult>({ url: '/coupons/trial', method: 'POST', data: payload })
}
