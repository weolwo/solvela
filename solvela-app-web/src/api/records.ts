import type { Id } from '@/types/contract'

import { request } from './http'

/**
 * 我的记录（奖励发放记录）。对应 <b>prize 域</b>，2026-09-05 已接通真实接口。
 *
 * <h3>三种玩法共用一张表，所以这里只有一个接口</h3>
 * 抽奖、任务、彩票的发奖都落在 `t_prize_log`（表上有 activity_type 列），
 * 所以「我的记录」是一张表的查询，不是跨三个玩法的聚合。
 *
 * <h3>失败原因不下发</h3>
 * 后端有 `failReason`，但那是<b>内部原因</b>（「资产账户冻结」「奖池库存不足」），
 * 对用户既没有可操作性，又暴露内部结构。所以 `statusText` 里只有
 * 「发放失败，请联系客服」—— 真正的原因客服按记录 id 在后台查得到。
 */

/** 一条奖励发放记录 */
export interface RecordItem {
  recordId: Id
  /** 给用户看的标题，就是奖品名 */
  title: string
  /** 展示用的状态文案，由后端给 —— 前端做映射表就是第二份状态机 */
  statusText: string
  /** 只用于选颜色，不直接展示 */
  status: 'PENDING' | 'DONE' | 'FAILED'
  /** 数量/面值，字符串。实物类没有面值时为 null */
  amount: string | null
  createTime: string
}

/**
 * 我最近的奖励记录，按时间倒序。
 *
 * <p>⚠️ <b>只有最近若干条，没有分页</b>。「全部记录」是另一页的事，
 * 那时才需要分页，而分页形状由那一页的需求定（要不要跳页），现在不预先猜。
 *
 * <p>没有记录时返回空数组，不是 404 —— 新用户就是这个状态。
 */
export function fetchRecords(): Promise<RecordItem[]> {
  return request<RecordItem[]>({ url: '/records' })
}
