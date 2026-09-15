import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import MineView from '../MineView.vue'

/**
 * 「我的」页上的入口。
 *
 * <h3>🔴 这条测试对应一个真实的交付事故</h3>
 * 2026-09-15：消息中心的路由、页面、接口、后端全做完了，**却没有任何地方
 * 能点进去** —— `/messages` 只能手输 URL 才到得了。
 *
 * <p>它一路没被发现，是因为每一层单独看都是对的：路由注册了、页面渲染正常、
 * 组件测试挂载的是页面本身（绕过了入口）、后端接口也通。
 * <b>没有任何一层负责回答「用户怎么到这一页」。</b>
 *
 * <h3>所以这里守的是「可达性」，不是页面内容</h3>
 * 断言的是一级页上真的存在指向这些二级页的链接。加了新的二级页却忘了给入口，
 * 这条测试不会自动发现 —— 但至少已有的这几个入口被谁删掉时会红。
 *
 * <p>⚠️ 覆盖不到：TabBar 上的入口、活动页里的入口、以及「新页面忘了加入口」
 * 这个类型本身。那需要一条「每个非详情路由都要被某处引用」的规则，
 * 而详情页、分享页这些本来就只能从别处跳进来，规则会有一堆例外。
 */

vi.mock('@/api/assets', () => ({
  fetchAssets: () => Promise.resolve([]),
}))

const unreadSpy = vi.fn(() => Promise.resolve(7))

vi.mock('@/api/notification', () => ({
  fetchUnreadCount: () => unreadSpy(),
}))

/** 这一页上应该存在的入口：路由名 → 给人看的名字 */
const ENTRIES: [string, string][] = [
  ['messages', '消息'],
  // 2026-09-15 阶段 4：券第一次能被用掉了。找不到券的话，能用也等于没有
  ['coupons', '我的券包'],
  // 2026-09-15 阶段 7：券的第一个非商城出口
  ['recharge', '充话费'],
  ['records-exchange', '兑换记录'],
  ['records-promo', '优惠记录'],
  ['favorites', '我的收藏'],
  ['address-list', '地址簿'],
  ['settings', '设置'],
  ['theme', '主题'],
]

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    ...ENTRIES.map(([name]) => ({
      path: `/${name}`,
      name,
      component: { template: '<div />' },
    })),
    { path: '/login', name: 'login', component: { template: '<div />' } },
  ],
})

const global = { plugins: [router] }

beforeEach(() => {
  setActivePinia(createPinia())
  unreadSpy.mockClear()
})

async function settle(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
}

describe('「我的」页的入口', () => {
  it.each(ENTRIES)('有通往 %s（%s）的链接', async (name, label) => {
    const w = mount(MineView, { global })
    await settle()

    // Cell 传的是【命名路由对象】（{ name: 'messages' }），不是路径字符串 ——
    // 所以要取 to.name，String(to) 会得到 [object Object]
    const targets = w
      .findAllComponents({ name: 'RouterLink' })
      .map((l) => l.props('to'))
      .map((to) => (typeof to === 'object' && to !== null && 'name' in to ? String(to.name) : String(to)))

    expect(
      targets.includes(name),
      `「我的」页上找不到通往 ${label} 的入口 —— 页面做好了但用户到不了。实际有：${targets.join(', ')}`,
    ).toBe(true)
  })

  it('🔴 消息入口显示未读数', async () => {
    const w = mount(MineView, { global })
    await settle()

    expect(unreadSpy).toHaveBeenCalledTimes(1)
    // 服务端把通知和公告两个数加好再下发，端上不自己相加
    expect(w.text()).toContain('7 条未读')
  })

  it('未读数拉不到时入口照样在，只是不显示数字', async () => {
    unreadSpy.mockImplementationOnce(() => Promise.reject(new Error('网络炸了')))

    const w = mount(MineView, { global })
    await settle()

    // 为一次接口抖动把整行藏起来，是拿次要目标伤害主要目标
    expect(w.text()).toContain('消息')
    expect(w.text()).not.toContain('条未读')
  })
})
