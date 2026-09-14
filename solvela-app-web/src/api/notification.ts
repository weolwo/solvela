import { type Id, type Raw, toId } from '@/types/contract'

import { request, requestVoid } from './http'

/**
 * 消息中心。
 *
 * 🔴 **通知和公告是两件事，各走各的接口，端上也是两个 tab。**
 *
 * - **通知**（{@link fetchNotifications}）—— 写给你一个人的：中奖、发货、账号受限
 * - **公告**（{@link fetchAnnouncements}）—— 发给所有人的：系统维护、活动预告
 *
 * 底层的存储模型完全不同（通知一人一条，公告一条内容一行 + 一个已读游标），
 * 已读语义也不同（见 {@link readAnnouncement}）。合成一个列表的话，
 * 用户会在同一个列表里看到两种已读行为，那看着就是 bug。
 */

/** 分类。SYSTEM 关不掉，所以设置页里没有它的开关 */
export type NotificationCategory = 'SYSTEM' | 'TRADE' | 'MARKETING'

/**
 * 通知列表的一行。
 *
 * 🔴 **没有 title 和 content**，只有 `summary`。
 * 正文是服务端按「发送当时那一版模板」现渲染的，列表页不需要它 ——
 * 一页几十条就是几十次渲染，全是白干的。摘要本身已经是可读的一句话。
 */
export interface NotificationItem {
  id: Id
  templateCode: string
  category: NotificationCategory
  summary: string
  /** 0-未读 1-已读 */
  readFlag: number
  createTime: string
}

/** 通知详情：这里才有渲染好的标题和正文 */
export interface NotificationDetail {
  id: Id
  templateCode: string
  category: NotificationCategory
  title: string
  content: string
  readFlag: number
  createTime: string
}

export interface NotificationPage {
  list: NotificationItem[]
  total: number
  /** 通知 tab 的未读数。和列表同一次请求返回，不会出现「列表 3 条未读、红点写 5」 */
  unreadCount: number
}

/**
 * 一条公告。
 *
 * 公告的 `content` 直接下发 —— 它是运营手写的成品，没有模板、没有占位符。
 * `unread` **由服务端算**，别在端上自己比 id：游标语义散到客户端去，
 * 三个端迟早有一个写错，而表现只是「红点数对不上」，没人会怀疑到前端。
 */
export interface AnnouncementItem {
  id: Id
  title: string
  content: string
  category: NotificationCategory
  /** true = 强制确认公告，要弹窗而不是塞进列表 */
  forceAck: boolean
  unread: boolean
  publishTime: string
}

/** 免打扰。🔴 没有 systemEnabled —— 系统通知关不掉，所以也画不出那个开关 */
export interface NotificationPreference {
  tradeEnabled: boolean
  marketingEnabled: boolean
}

function normalizeNotification(raw: Raw<NotificationItem>): NotificationItem {
  return { ...raw, id: toId(raw.id) }
}

function normalizeAnnouncement(raw: Raw<AnnouncementItem>): AnnouncementItem {
  return { ...raw, id: toId(raw.id) }
}

// ---------------------------------------------------------------- 通知 tab

export function fetchNotifications(params: {
  category?: NotificationCategory
  unreadOnly?: boolean
  pageNum?: number
  pageSize?: number
}): Promise<NotificationPage> {
  return request<Raw<NotificationPage>>({ url: '/notification', params }).then((page) => ({
    ...page,
    list: (page.list ?? []).map(normalizeNotification),
  }))
}

/**
 * 通知详情。
 *
 * ⚠️ **打开即已读** —— 服务端在返回详情时就把它标记了，端上不用再调一次。
 * 所以拿到结果之后记得把本地列表里那一条的 `readFlag` 也改掉，否则返回列表页
 * 会看到一条「已经点开过却还标着未读」的记录。
 */
export function fetchNotificationDetail(id: Id): Promise<NotificationDetail> {
  return request<Raw<NotificationDetail>>({ url: `/notification/${id}` }).then((raw) => ({
    ...raw,
    id: toId(raw.id),
  }))
}

/** 通知全部已读。一次请求，返回本次标记的条数 */
export function readAllNotifications(): Promise<number> {
  return request<number>({ url: '/notification/read-all', method: 'POST' })
}

// ---------------------------------------------------------------- 公告 tab

/**
 * 公告列表。
 *
 * @param lastId 上一页最后一条的 id，首页不传。**用游标翻页而不是页码** ——
 *   公告会持续新增，页码分页在第 2 页会重复看到被挤下来的那一条。
 */
export function fetchAnnouncements(lastId?: Id, limit?: number): Promise<AnnouncementItem[]> {
  return request<Raw<AnnouncementItem>[]>({
    url: '/notification/announcement',
    params: { lastId, limit },
  }).then((l) => l.map(normalizeAnnouncement))
}

/**
 * 读了某条公告。
 *
 * 🔴 **公告不支持跳读**：点开任何一条，它**之前**的全部算已读。
 * 这和通知那边逐条已读是不同的语义 —— 两者分成两个 tab 正是为了让用户
 * 不会在同一个列表里看到两种行为。
 */
export function readAnnouncement(id: Id): Promise<void> {
  return requestVoid({ url: `/notification/announcement/${id}/read`, method: 'POST' })
}

export function readAllAnnouncements(): Promise<void> {
  return requestVoid({ url: '/notification/announcement/read-all', method: 'POST' })
}

/**
 * 进 App 时查：有没有必须先确认才能继续用的公告。
 *
 * 非空就弹窗，用户点了「我已阅读」再调 {@link ackAnnouncement}。
 * **没确认就一直返回** —— 所以不用担心用户关掉弹窗就再也看不到。
 */
export function fetchPendingAck(): Promise<AnnouncementItem[]> {
  return request<Raw<AnnouncementItem>[]>({
    url: '/notification/announcement/pending-ack',
  }).then((l) => l.map(normalizeAnnouncement))
}

/** 我已阅读并知悉。IP 由服务端记录，端上不传也不该传 */
export function ackAnnouncement(id: Id): Promise<void> {
  return requestVoid({ url: `/notification/announcement/${id}/ack`, method: 'POST' })
}

// ---------------------------------------------------------------- 红点与设置

/**
 * 入口总红点 = 通知未读 + 公告未读。
 *
 * **服务端加好再下发**，端上不要自己拉两个数相加 —— 那样迟早漏掉公告那一半。
 */
export function fetchUnreadCount(): Promise<number> {
  return request<number>({ url: '/notification/unread-count' })
}

export function fetchPreference(): Promise<NotificationPreference> {
  return request<NotificationPreference>({ url: '/notification/preference' })
}

export function savePreference(preference: NotificationPreference): Promise<void> {
  return requestVoid({
    url: '/notification/preference',
    method: 'POST',
    params: preference,
  })
}
