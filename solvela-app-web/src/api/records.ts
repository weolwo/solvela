import { type Id, type Raw, toId } from '@/types/contract'

import { request } from './http'

/**
 * 「我的记录」。<b>三种记录是三件事，各走各的接口。</b>
 *
 * <ul>
 *   <li><b>兑换记录</b>（{@link fetchExchangeRecords}）—— 我花积分换了什么</li>
 *   <li><b>优惠记录</b>（{@link fetchPromoRecords}）—— 平台要发给我什么，含还在路上的</li>
 *   <li><b>奖励记录</b>（{@link fetchPrizeRecords}）—— 我在某个活动里中了什么。
 *       它属于活动，所以展示在活动专题页而不是「我的」</li>
 * </ul>
 *
 * 合并成一个「全部记录」是这里的旧做法，问题是三者的状态机、金额口径、
 * 该显示什么完全不同，合成一个列表之后每条只能显示最小公约数。
 */

/** 奖励记录 / 优惠记录共用的形状：服务端已经把状态翻成人话了 */
export interface RecordItem {
  recordId: Id
  title: string
  statusText: string
  status: 'PENDING' | 'DONE' | 'FAILED'
  /** 面值。实物类没有，为 null */
  amount: string | null
  createTime: string
}

/**
 * 一条兑换记录。
 *
 * 🔴 `cost` 是服务端拼好的整句（「45,000 积分 + ¥299.00」）。
 * 让端上自己拼积分和现金那两半，三个页面就会拼出三种样子。
 */
export interface OrderItem {
  orderNo: string
  commodityName: string
  coverUrl: string | null
  /** 规格，如 ["颜色：曜石黑", "尺码：L"]。无规格商品是空数组 */
  specs: string[]
  quantity: number
  cost: string
  statusText: string
  status: 'PENDING' | 'DONE' | 'FAILED'
  /** 状态之外还要说的那句（「积分未退回」）。没有就是 null，别画那一行 */
  hint: string | null
  createTime: string
}

/** 反序列化边界：Long 小值下发为数字，在这里归一成字符串。见 types/contract.ts 的 Raw */
function normalizeRecord(raw: Raw<RecordItem>): RecordItem {
  return { ...raw, recordId: toId(raw.recordId) }
}

/** 兑换记录。走商城，不走 /records —— 它是商城的东西 */
export function fetchExchangeRecords(): Promise<OrderItem[]> {
  return request<OrderItem[]>({ url: '/mall/order' })
}

/** 优惠记录。底层是提案记录，但「提案」是运营的词，C 端不出现 */
export function fetchPromoRecords(): Promise<RecordItem[]> {
  return request<Raw<RecordItem>[]>({ url: '/records/promo' }).then((l) => l.map(normalizeRecord))
}

/**
 * 奖励记录。
 *
 * @param activityCode 只看这一个活动的。<b>过滤在服务端做</b> ——
 *   拿最近 20 条回来再筛，在参与多个活动的用户身上会筛出空列表，
 *   而他明明在这个活动里中过奖，只是那条排在第 21 位。
 */
export function fetchPrizeRecords(activityCode?: string): Promise<RecordItem[]> {
  return request<Raw<RecordItem>[]>({
    url: '/records/prize',
    params: activityCode === undefined ? {} : { activityCode },
  }).then((l) => l.map(normalizeRecord))
}
