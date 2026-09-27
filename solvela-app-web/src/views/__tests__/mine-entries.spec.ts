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

/*
 * 2026-09-27 重排后这一页要各拉一份列表来数数（券包 / 彩票 / 奖品 / 收藏）和等级名。
 * 🔴 漏 mock 一个的表现不是断言失败，而是 jsdom 里真发请求、整页卡在加载态。
 */
const deliveriesSpy = vi.fn(() =>
  Promise.resolve([
    { deliveryId: '1', needAddress: true, status: 0 },
    { deliveryId: '2', needAddress: false, status: 0 },
    { deliveryId: '3', needAddress: false, status: 1 },
  ]),
)
vi.mock('@/api/delivery', () => ({ fetchDeliveries: () => deliveriesSpy() }))
vi.mock('@/api/coupons', () => ({ fetchCoupons: () => Promise.resolve([{}, {}, {}]) }))
vi.mock('@/api/lottery', () => ({ fetchMyTickets: () => Promise.resolve([{}, {}]) }))
vi.mock('@/api/mall', () => ({ fetchFavorites: () => Promise.resolve([{}]) }))
vi.mock('@/api/grade', () => ({ fetchMyGrade: () => Promise.resolve({ gradeName: 'V2 白银' }) }))

/** 这一页上应该存在的入口：路由名 → 给人看的名字 */
const ENTRIES: [string, string][] = [
  ['messages', '消息'],
  // 2026-09-20 阶段 4：保级缓冲期唯一的用户侧出口。
  // 没有这个入口的话，「等级到期 → 三个月宽限 → 成长值双倍」全程用户无感，
  // 而那套挽留机制正是做等级体系要换的东西
  ['grade', '会员中心'],
  // 2026-09-15 阶段 4：券第一次能被用掉了。找不到券的话，能用也等于没有
  ['coupons', '我的券包'],
  // 2026-09-15 阶段 7：券的第一个非商城出口
  ['recharge', '充话费'],
  ['records-exchange', '兑换记录'],
  // 2026-09-18：实物履约三段式里第 ② 步的入口。做完页面却忘了给入口，
  // 正是这条测试当初为之而生的那个事故
  ['deliveries', '我的实物奖品'],
  // 2026-09-18：彩票玩法后端全建成了，而会员此前拿不到也看不到
  ['lottery-tickets', '我的彩票'],
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
    //
    // ⚠️ props() 的返回类型是 any，不标注的话整条链都会变成 any，
    //    @typescript-eslint/no-unsafe-* 会在下一行的 to.name 上报出来。
    //    标成 RouterLink 真正接受的那两种形状，而不是 `as any` 了事。
    //
    //    分支判 `typeof to === 'string'` 而不是判 'object'：后者在 else 里
    //    只把「不带 name 的对象」排除掉了，to 仍然可能是对象，于是 String(to)
    //    还是会得到 [object Object] —— no-base-to-string 报的就是这个。
    //    按字符串正面判，两个分支才都是确定的。
    const targets = w
      .findAllComponents({ name: 'RouterLink' })
      .map((l) => l.props('to') as string | { name?: string })
      .map((to) => (typeof to === 'string' ? to : String(to.name)))

    expect(
      targets.includes(name),
      `「我的」页上找不到通往 ${label} 的入口 —— 页面做好了但用户到不了。实际有：${targets.join(', ')}`,
    ).toBe(true)
  })

  it('🔴 消息铃铛显示未读数，读屏能听到「几条未读」', async () => {
    const w = mount(MineView, { global })
    await settle()

    expect(unreadSpy).toHaveBeenCalledTimes(1)
    // 服务端把通知和公告两个数加好再下发，端上不自己相加
    const bell = w.find('.profile__bell')
    expect(bell.find('.profile__badge').text()).toBe('7')
    // 红点只是一个数字，对读屏没有意义 —— 名字要把话说全
    expect(bell.attributes('aria-label')).toBe('消息，7 条未读')
  })

  it('未读数拉不到时铃铛照样在，只是没有红点', async () => {
    unreadSpy.mockImplementationOnce(() => Promise.reject(new Error('网络炸了')))

    const w = mount(MineView, { global })
    await settle()

    // 为一次接口抖动把入口藏起来，是拿次要目标伤害主要目标
    expect(w.find('.profile__bell').exists()).toBe(true)
    expect(w.find('.profile__badge').exists()).toBe(false)
  })

  it('资产数字一眼可见：券包 / 彩票 / 奖品 / 收藏', async () => {
    const w = mount(MineView, { global })
    await settle()

    const stats = w.findAll('.stats__item').map((s) => s.text())
    expect(stats).toEqual(['3券包', '2彩票', '3奖品', '1收藏'])
    // 等级名在名字旁边
    expect(w.find('.profile__grade').text()).toContain('V2 白银')
  })

  it('🔴 奖品按要不要我动手分组；「待填地址」用 needAddress，有数时变显眼', async () => {
    const w = mount(MineView, { global })
    await settle()

    const groups = w.findAll('.grid--3 .grid__item')
    expect(groups.map((g) => g.find('.grid__label').text())).toEqual([
      '待填地址',
      '待发货',
      '已发货',
    ])
    // needAddress 的那一单不能再被算进「待发货」—— 它此刻卡在用户，不在仓库
    expect(groups.map((g) => g.find('.grid__count').text())).toEqual(['1', '1', '1'])
    expect(groups[0]?.classes()).toContain('grid__item--alert')
  })

  it('数字拉不到时显示「—」而不是 0，入口照样在', async () => {
    deliveriesSpy.mockImplementationOnce(() => Promise.reject(new Error('网络炸了')))

    const w = mount(MineView, { global })
    await settle()

    // 0 是一个确定的答案，不知道的时候不能说 0
    expect(w.findAll('.stats__item')[2]?.text()).toBe('—奖品')
    expect(w.findAll('.grid--3 .grid__count')).toHaveLength(0)
  })
})
