import { type Id, type Raw, toId } from '@/types/contract'

import { request } from './http'

/**
 * 我的实物奖品：中奖 / 兑换的实物寄到哪了。
 *
 * <h3>它补的是三段式履约里一直缺的第 ②</h3>
 * 后端 `PhysicalAssetHandler` 的注释把实物履约写成三段：
 * ① 中奖生成履约单（**此刻不知道寄到哪**）→ ② 用户在 C 端补填收货信息
 * → ③ 运营发货、回填物流单号。
 *
 * <p>第 ② 步一直没有入口 —— 中了实物奖之后用户侧是断的：看不到、也填不了地址，
 * 只能等客服。`t_physical_delivery.receiver_name` 那一列的注释写着
 * 「中奖时未知，由用户后续补填」，那句话挂了很久。
 *
 * <h3>🔴 收件电话是脱敏值</h3>
 * 与地址簿同一口径（`138****8000`）。要寄快递用的真号码<b>只在服务端之间传</b> ——
 * 端上永远拿不到，也不需要。
 *
 * <h3>补填只传 addressId，不传姓名电话地址</h3>
 * 让端上传明文的话「寄到哪」就成了客户端说了算，服务端无从校验 ——
 * 和商城下单只传 addressId 是同一条规矩。要寄新地址，先去地址簿加一条。
 */

/** 一张实物履约单 */
export interface DeliveryItem {
  deliveryId: Id
  /** 奖品 / 商品名。老单子（加列之前建的）后端会给「实物奖品」这个兜底 */
  prizeName: string
  /** 来源：`PROPOSAL` 中奖 / `MALL` 商城兑换 */
  sourceType: string
  /** 来源单号：提案 id 或商城订单号。客诉时靠它对上游 */
  sourceBizId: string
  /** -1 已取消 / 0 待发货 / 1 已发货 / 2 已签收 / 3 异常退回 */
  status: number | null
  /**
   * 给用户看的那句话。**由后端给** —— 前端做映射表就是第二份状态机，
   * 资产域加一个状态时它会静默变错。
   */
  statusText: string
  /**
   * 还差收货信息。**这是「填写收货信息」按钮唯一的判据**。
   *
   * 🔴 别自己按 status 推：什么时候还能填是状态机的一部分，
   * 各端各推一份的话，状态机改一次就会有一个端开始给出错的按钮。
   * 和 `OrderItem.payable` 是同一条规矩。
   */
  needAddress: boolean
  /** 没填过是 null */
  receiverName: string | null
  /** **脱敏值**。没填过是 null */
  receiverPhone: string | null
  receiverAddress: string | null
  /** 未发货是 null */
  logisticsCompany: string | null
  logisticsNo: string | null
  createTime: string
}

/** 补填结果。`accepted: false` 不是故障 —— 多半是运营刚好把它发出去了 */
export interface DeliveryFillResult {
  accepted: boolean
  /** 给用户看的一句话，**成功时也有**。直接显示，不要自己再拼一句 */
  message: string
}

/** 反序列化边界：Long 小值下发为数字，在这里归一成字符串。见 types/contract.ts 的 Raw */
function normalize(raw: Raw<DeliveryItem>): DeliveryItem {
  return { ...raw, deliveryId: toId(raw.deliveryId) }
}

/**
 * 我的实物奖品。中奖的和商城兑的**混在一起** ——
 * 用户不关心「这件东西是抽中的还是兑的」，他只想知道「我的东西呢」。
 *
 * <p>没有就返回空数组，不是 404。
 */
export function fetchDeliveries(): Promise<DeliveryItem[]> {
  return request<Raw<DeliveryItem>[]>({ url: '/delivery' }).then((list) => list.map(normalize))
}

/**
 * 用地址簿里的一个地址补填收货信息。
 *
 * ⚠️ 服务端那一侧会再校验一次「这单是不是你的、现在还能不能填」——
 * 页面上按钮的显隐只是省掉一次必然失败的请求，不是权限本身。
 */
export function fillDeliveryAddress(deliveryId: Id, addressId: Id): Promise<DeliveryFillResult> {
  return request<DeliveryFillResult>({
    url: `/delivery/${deliveryId}/address`,
    method: 'POST',
    params: { addressId },
  })
}
