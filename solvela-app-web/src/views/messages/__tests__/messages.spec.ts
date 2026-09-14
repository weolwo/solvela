import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { AnnouncementItem, NotificationItem } from '@/api/notification'

import MessagesView from '../MessagesView.vue'

/**
 * 消息中心。
 *
 * <h3>这里守的核心只有一条：两种已读语义不能串</h3>
 * <ul>
 *   <li><b>通知</b>逐条已读 —— 点第 5 条，前 4 条仍未读；</li>
 *   <li><b>公告</b>是游标 —— 点第 5 条，<b>它和它之前的全部</b>变已读。</li>
 * </ul>
 *
 * 服务端那边公告只存一个 `last_read_id`，压根没有「只读了中间那条」这种状态。
 * 端上如果按逐条去渲染未读，用户会看到「我点了，上面几条怎么还是红点」——
 * 而刷新一下它们又都没了。这种 bug 只会被当成「偶尔抽风」。
 *
 * <p>两者分成两个 tab 正是为了让用户不会在同一个列表里撞见两种规则。
 */

const NOTIFICATIONS: NotificationItem[] = [
  {
    id: '3001' as NotificationItem['id'],
    templateCode: 'PRIZE_WON',
    category: 'MARKETING',
    summary: '您获得的 iPhone 15 Pro ×1 已发放到您的账户',
    readFlag: 0,
    createTime: '2026-09-14 10:00:00',
  },
  {
    id: '3000' as NotificationItem['id'],
    templateCode: 'DELIVERY_SHIPPED',
    category: 'TRADE',
    summary: '您的订单 M2026 已发货',
    readFlag: 1,
    createTime: '2026-09-13 10:00:00',
  },
]

/* 按 id 倒序，和服务端一致 —— 游标语义依赖这个顺序 */
const ANNOUNCEMENTS: AnnouncementItem[] = [
  {
    id: '203' as AnnouncementItem['id'],
    title: '最新公告',
    content: '最新内容',
    category: 'SYSTEM',
    forceAck: false,
    unread: true,
    publishTime: '2026-09-14 09:00:00',
  },
  {
    id: '202' as AnnouncementItem['id'],
    title: '中间公告',
    content: '中间内容',
    category: 'MARKETING',
    forceAck: false,
    unread: true,
    publishTime: '2026-09-13 09:00:00',
  },
  {
    id: '201' as AnnouncementItem['id'],
    title: '最旧公告',
    content: '最旧内容',
    category: 'MARKETING',
    forceAck: false,
    unread: true,
    publishTime: '2026-09-12 09:00:00',
  },
]

const readAnnouncementSpy = vi.fn(() => Promise.resolve())
const readAllNotificationsSpy = vi.fn(() => Promise.resolve(2))

vi.mock('@/api/notification', async (importOriginal) => {
  /* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
  const actual = await importOriginal<typeof import('@/api/notification')>()
  return {
    ...actual,
    fetchNotifications: () =>
      Promise.resolve({ list: NOTIFICATIONS, total: 2, unreadCount: 1 }),
    fetchAnnouncements: (lastId?: string) =>
      Promise.resolve(lastId === undefined ? ANNOUNCEMENTS : []),
    readAnnouncement: (id: string) => readAnnouncementSpy(id),
    readAllNotifications: () => readAllNotificationsSpy(),
    readAllAnnouncements: () => Promise.resolve(),
  }
})

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/messages/:id', name: 'message-detail', component: { template: '<div />' } },
  ],
})

const global = { plugins: [router] }

beforeEach(() => {
  setActivePinia(createPinia())
  readAnnouncementSpy.mockClear()
  readAllNotificationsSpy.mockClear()
})

async function settle(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
}

describe('通知 tab', () => {
  it('渲染摘要而不是正文 —— 列表页不该为了几十条各渲染一次模板', async () => {
    const w = mount(MessagesView, { global })
    await settle()
    expect(w.text()).toContain('您获得的 iPhone 15 Pro ×1 已发放到您的账户')
  })

  it('未读的才画红点', async () => {
    const w = mount(MessagesView, { global })
    await settle()
    // 两条记录，只有一条未读
    expect(w.findAll('.card__dot--on')).toHaveLength(1)
  })

  it('全部已读之后红点清空，且不整页重拉', async () => {
    const w = mount(MessagesView, { global })
    await settle()

    await w.find('.page__link').trigger('click')
    await settle()

    expect(readAllNotificationsSpy).toHaveBeenCalledTimes(1)
    expect(w.findAll('.card__dot--on')).toHaveLength(0)
    // 「全部已读」按钮本身也该消失 —— 没有未读了还留着它，点一次是一次空请求
    expect(w.find('.page__link').exists()).toBe(false)
  })
})

describe('公告 tab', () => {
  async function openAnnouncementTab() {
    const w = mount(MessagesView, { global })
    await settle()
    // Segmented 的第二项就是公告
    await w.findAll('.sv-segmented__item')[1]?.trigger('click')
    await settle()
    return w
  }

  it('切到公告 tab 显示公告列表', async () => {
    const w = await openAnnouncementTab()
    expect(w.text()).toContain('最新公告')
    expect(w.text()).toContain('最旧公告')
  })

  it('点开才展示正文 —— 列表默认只有标题', async () => {
    const w = await openAnnouncementTab()
    expect(w.text()).not.toContain('中间内容')

    await w.findAll('.card__head')[1]?.trigger('click')
    await settle()
    expect(w.text()).toContain('中间内容')
  })

  it('🔴 点开中间那条，它【以及更旧的】全部变已读 —— 这是游标语义', async () => {
    const w = await openAnnouncementTab()
    expect(w.findAll('.card__dot--on')).toHaveLength(3)

    // 点第二条（id=202）
    await w.findAll('.card__head')[1]?.trigger('click')
    await settle()

    expect(readAnnouncementSpy).toHaveBeenCalledWith('202')
    /*
     * 服务端只存一个 last_read_id，没有「只读了中间那条」这种状态。
     * 所以 202 和比它更旧的 201 都变已读，只剩最新的 203 还是未读。
     *
     * 端上如果按逐条渲染，用户会看到「我点了，下面那条怎么还是红点」，
     * 而刷新一下它又没了 —— 那种 bug 只会被当成偶尔抽风。
     */
    expect(w.findAll('.card__dot--on')).toHaveLength(1)
  })

  it('已读的公告再点开不会重复调接口', async () => {
    const w = await openAnnouncementTab()
    await w.findAll('.card__head')[2]?.trigger('click')
    await settle()
    readAnnouncementSpy.mockClear()

    // 收起再展开同一条：它已经是已读了，不该再发请求
    await w.findAll('.card__head')[2]?.trigger('click')
    await settle()
    await w.findAll('.card__head')[2]?.trigger('click')
    await settle()

    expect(readAnnouncementSpy).not.toHaveBeenCalled()
  })
})
