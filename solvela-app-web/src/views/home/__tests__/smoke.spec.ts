import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { CommodityQuery } from '@/api/mall'
import type { Id } from '@/types/contract'

import Home from '../Home.vue'
import Mall from '../Mall.vue'
import Tasks from '../Tasks.vue'

/*
 * jsdom 没有实现 window.matchMedia。Carousel / Wheel 在 setup 里读
 * prefers-reduced-motion，缺了它会直接抛 —— 而 Vue 的 patch 一旦抛异常，
 * 后续更新全部停摆，表现是画面永远冻在骨架屏（不报错、看着像请求没回来）。
 */
window.matchMedia = (query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
})

// 真实路由的登录守卫会 useAuthStore()，没有活动的 pinia 会直接抛
/*
 * 活动列表 2026-09-05 已接通真实后端，jsdom 里发不出去。
 * 本文件验的是首页/商城的渲染与取舍（只露前几条、焦点位点了去哪），
 * 活动数据是它的<b>输入</b> —— 钉在这里比依赖某个桩里的样例更清楚。
 */
/* mock 工厂里不能写 import() 类型注解（eslint），先在这里起个别名 */
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type MallModule = typeof import('@/api/mall')

vi.mock('@/api/promo', () => {
  const promos = [
    {
      activityCode: 'DEMO-MIDAUTUMN',
      activityName: '样例·中秋抽好礼',
      subTitle: '每天 3 次机会',
      themeColor: null,
      startTime: '2026-09-01 00:00:00',
      endTime: '2026-09-30 23:59:59',
      mainImageId: null,
      joinable: true,
    },
    {
      activityCode: 'DEMO-NEWCOMER',
      activityName: '样例·新人专享',
      subTitle: null,
      themeColor: null,
      startTime: '2026-12-01 00:00:00',
      endTime: '2026-12-31 23:59:59',
      mainImageId: null,
      joinable: false,
    },
  ]
  return {
    fetchPromos: () => Promise.resolve(promos),
    fetchBanners: () => Promise.resolve(promos.slice(0, 3)),
  }
})

/*
 * 任务中心 2026-09-05 已接通真实后端。这里钉住输入 —— 覆盖四种状态：
 * 未开始（没有记录）/ 进行中带进度 / 已发奖 / 有跳转入口。
 */
vi.mock('@/api/task', () => ({
  fetchTasks: () =>
    Promise.resolve([
      /*
       * 阶梯任务，取自真实配置（任务 51）：1 天得积分188、连签 5 天再得红包8。
       * 多档时 rewardText 为 null —— 后端不再把两档拼成「A / B」，
       * 那样用户看不出哪个奖对应哪一档，也看不出自己已经拿到了第一档。
       */
      {
        taskId: '1',
        taskName: '样例·每日签到',
        taskGroup: null,
        target: '5',
        current: '1',
        statusText: '进行中',
        finished: false,
        rewardText: null,
        stages: [
          { target: '1', rewardText: '积分188', reached: true },
          { target: '5', rewardText: '红包8', reached: false },
        ],
        ruleText: '连续完成 5 次，中断即清零',
        periodText: '不限',
        deadlineText: '9月30日 23:59',
        actionUrl: null,
      },
      {
        taskId: '2',
        taskName: '样例·逛商城',
        taskGroup: null,
        target: '1',
        current: '0',
        statusText: '未开始',
        finished: false,
        rewardText: '+20 积分',
        stages: [{ target: '1', rewardText: '+20 积分', reached: false }],
        ruleText: '完成一次即达标',
        periodText: '每日',
        deadlineText: null,
        actionUrl: '/mall',
      },
      {
        taskId: '3',
        taskName: '样例·完善资料',
        taskGroup: null,
        target: '1',
        current: '1',
        statusText: '已发奖',
        finished: true,
        rewardText: '+50 积分',
        stages: [{ target: '1', rewardText: '+50 积分', reached: true }],
        ruleText: '完成一次即达标',
        periodText: '仅一次',
        deadlineText: null,
        actionUrl: null,
      },
      /*
       * 运营填了一个路由表里没有的地址（真实配置：任务 51 填的是 /signIn，
       * 而 C 端至今没有签到页）。这一条用来验「不画通向 404 的按钮」。
       */
      {
        taskId: '4',
        taskName: '样例·连续签到',
        taskGroup: null,
        target: '5',
        current: '0',
        statusText: '未开始',
        finished: false,
        rewardText: '+188 积分',
        stages: [{ target: '5', rewardText: '+188 积分', reached: false }],
        ruleText: '连续完成 5 次，中断即清零',
        periodText: '每日',
        deadlineText: null,
        actionUrl: '/signIn',
      },
    ]),
}))

/*
 * 商城读路径 2026-09-05 已接通真实后端，jsdom 里发不出去。
 * 样例数据集中在 @/testing/fixtures —— 五个 spec 共用一份，
 * 免得「午夜蓝 38 库存是 8 还是 6」这种细节在文件之间漂。
 */
vi.mock('@/api/mall', async (importOriginal) => {
  const actual = await importOriginal<MallModule>()
  const fixtures = await import('@/testing/fixtures')
  const favorites = new Set(
    fixtures.COMMODITIES.filter((c) => c.favorite).map((c) => c.commodityId),
  )
  return {
    // 纯函数（groupSkuAttributes / findSku / isOptionAvailable / OrderStatus）保持真身
    ...actual,
    fetchCategories: () => Promise.resolve(fixtures.CATEGORIES),
    fetchCommodities: (query: CommodityQuery = {}) => {
      // 服务端筛选在测试里也要成立：断言的是「传了条件就只回那些」
      const list = fixtures.COMMODITIES.filter((c) => {
        if (
          query.categoryId !== null &&
          query.categoryId !== undefined &&
          c.categoryId !== query.categoryId
        )
          return false
        const kw = (query.keyword ?? '').trim()
        return kw === '' || c.commodityName.includes(kw)
      }).map((c) => ({ ...c, favorite: favorites.has(c.commodityId) }))
      return Promise.resolve({ list, total: list.length })
    },
    fetchCommodityDetail: (id: Id) => Promise.resolve(fixtures.detailOf(id)),
    fetchFavorites: () =>
      Promise.resolve(
        fixtures.COMMODITIES.filter((c) => favorites.has(c.commodityId)).map((c) => ({
          ...c,
          favorite: true,
        })),
      ),
    toggleFavorite: (id: Id, on: boolean) => {
      if (on) favorites.add(id)
      else favorites.delete(id)
      return Promise.resolve()
    },
    redeem: () => Promise.resolve(fixtures.REDEEM_RESULT),
  }
})

setActivePinia(createPinia())

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    // Home 里有去商城 / 活动中心的入口链接，路由表缺了会直接抛 No match
    { path: '/mall', name: 'mall', component: { template: '<div/>' } },
    { path: '/activities', name: 'activities', component: { template: '<div/>' } },
    // ProductCard 整张卡是去详情页的链接，路由表缺了会 No match
    { path: '/product/:id', name: 'product', component: { template: '<div/>' } },
    { path: '/activity/:code', name: 'activity', component: { template: '<div/>' } },
  ],
})

// ui/ 下的组件由 unplugin-vue-components 自动注册（见 vitest.config），这里不用手动挂
const global = { plugins: [router] }

/**
 * 等一次微任务队列就够 —— 2026-09-05 起这些接口在测试里是 vi.mock 的，
 * 同步 resolve，不再有当初那个 300~450ms 的桩延迟。
 * 还留一个 0ms 的 setTimeout 是因为 useAsync 里那条链有一层 await。
 */
async function settle(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
}

describe('Mall', () => {
  it('渲染分类、商品与对价', async () => {
    const w = mount(Mall, { global })
    await settle()
    const html = w.html()
    expect(html).toContain('数码3C')
    expect(html).toContain('样例·Apple Watch Series 6')
    // 对价走 utils/cost：纯积分一种写法，积分+现金另一种
    expect(html).toContain('45,000 积分')
    expect(html).toContain('45,000 积分 + ¥299.00')
    // 划线位是「值多少钱」（original_price 是现金），不是划掉的积分
    expect(html).toContain('价值 ¥3,199.00')
    expect(w.findAll('.card')).toHaveLength(4)
  })

  it('点分类只留该分类的商品', async () => {
    const w = mount(Mall, { global })
    await settle()
    const chip = w.findAll('.cats__item').find((b) => b.text().includes('数码3C'))
    await chip?.trigger('click')
    await settle()
    // 分类筛选发生在服务端：categoryId=1 的两件
    expect(w.findAll('.card')).toHaveLength(2)
    expect(w.html()).not.toContain('纯棉圆领')
  })

  it('🔴 搜索走服务端，不是本地过滤 —— 分页之后本地过滤只能搜到当前页', async () => {
    const w = mount(Mall, { global })
    await settle()
    await w.find('.search__btn--open').trigger('click')
    await w.find('.search__input').setValue('T 恤')
    // 关键词有防抖，等它过去再等请求回来
    await new Promise((r) => setTimeout(r, 400))
    await settle()
    expect(w.findAll('.card')).toHaveLength(1)
    expect(w.html()).toContain('纯棉圆领')
  })

  it('商城里没有轮播图 —— 那是首页的东西', async () => {
    const w = mount(Mall, { global })
    await settle()
    expect(w.find('.sv-carousel').exists()).toBe(false)
  })
})

describe('Home', () => {
  it('有轮播图，商品与活动都只露前几条', async () => {
    const w = mount(Home, { global })
    await settle()
    expect(w.find('.sv-carousel').exists()).toBe(true)
    // 桩里有 8 件商品 / 2 场活动，首页分别只取 4 条和 2 条
    expect(w.findAll('.card')).toHaveLength(4)
    expect(w.findAll('.promo')).toHaveLength(2)
    expect(w.html()).toContain('逛商城')
    expect(w.html()).toContain('全部活动')
  })

  it('焦点位按钮抛出活动编码', async () => {
    const w = mount(Home, { global })
    await settle()
    await w.find('.sv-carousel__cta').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.params.code).toBe('DEMO-MIDAUTUMN')
  })

  it('收藏是乐观更新，点了立刻翻转心形', async () => {
    const w = mount(Home, { global })
    await settle()
    const first = w.findAll('.card__fav')[0]
    /*
     * 断言的是「翻转」而不是「变成实心」：桩的收藏状态是 module 级的，
     * 同一个文件里前面的用例可能已经把它点过一遍。
     * 依赖初始值的写法会随用例顺序时好时坏。
     */
    const before = first?.classes().includes('card__fav--on')
    await first?.trigger('click')
    expect(w.findAll('.card__fav')[0]?.classes().includes('card__fav--on')).toBe(!before)
  })
})

describe('Tasks', () => {
  it('渲染任务、进度条与奖励，且**没有领取按钮**', async () => {
    const w = mount(Tasks, { global })
    await settle()
    const html = w.html()
    expect(html).toContain('样例·每日签到')
    // 单档任务照旧给一行摘要
    expect(html).toContain('+20 积分')

    /*
     * 🔴 这条是这个用例真正的价值：后端任务达标即自动发奖，
     * 状态里没有 CLAIMED。第一版前端凭空造了「领取」按钮，
     * 这里断言它不会回来 —— 一个点了什么都不会发生的按钮比不画更糟。
     */
    // 用 text() 不用 html()：html() 会把模板注释也算进来，
    // 而 Tasks.vue 的注释里正好有「没有「领取」按钮」这句话
    expect(w.text()).not.toContain('领取')

    // 进度条只给「目标 > 1 且还没发奖」的任务画：签到(5) 与连续签到(5) 两条
    expect(w.findAll('[role="progressbar"]')).toHaveLength(2)
    // 满格值是**最高档的阈值**（5），不是 rule_config 里那个数
    expect(w.find('[role="progressbar"]').attributes('aria-valuemax')).toBe('5')
    expect(html).toContain('1/5')
  })

  it('🔴 阶梯任务把每一档分开画，已达标的那档看得出来', async () => {
    const w = mount(Tasks, { global })
    await settle()

    const stages = w.findAll('.ladder__item')
    expect(stages).toHaveLength(2)
    /*
     * 旧版把两档压成「积分188 / 红包8」一句话：用户看不出哪个奖对应哪一档，
     * 更看不出签到 1 天之后自己已经拿到了第一档。
     */
    expect(stages[0]?.text()).toContain('积分188')
    expect(stages[1]?.text()).toContain('红包8')
    // 阈值要各自写出来，否则「1 天」和「5 天」的区别就没了
    expect(stages[0]?.text()).toContain('1')
    expect(stages[1]?.text()).toContain('5')

    // 已达标的那档要有视觉区别 —— 不然拿到了 188 积分，界面上毫无变化
    expect(stages[0]?.classes()).toContain('ladder__item--reached')
    expect(stages[1]?.classes()).not.toContain('ladder__item--reached')
  })

  it('🔴 actionUrl 指向不存在的路由时不画「去完成」，不能把 404 递给用户', async () => {
    const w = mount(Tasks, { global })
    await settle()

    /*
     * 运营填的是自由文本。任务 51 真填了 /signIn，而路由表里没有 ——
     * 原先原样渲染成链接，点下去落到 catch-all，用户看到 404，
     * 还会以为是自己的问题或者任务系统坏了。
     */
    expect(w.text()).toContain('样例·连续签到')
    const links = w.findAll('.task__go')
    // 三个候选里只有 /mall 那条是能跳的：signIn 不存在，已完成的那条不画
    expect(links).toHaveLength(1)
    expect(links[0]?.attributes('href')).toBe('/mall')
  })

  it('站内链接走 RouterLink，不是整页刷新的 <a>', async () => {
    const w = mount(Tasks, { global })
    await settle()
    const link = w.findComponent({ name: 'RouterLink' })
    // <a href> 对站内路径是整页刷新：白屏一次，内存里的状态全丢
    expect(link.exists()).toBe(true)
  })

  it('🔴 点任务行打开详情，规则/周期/截止/档位都在里面', async () => {
    const w = mount(Tasks, { global })
    await settle()

    // 详情没打开时不该有弹层
    expect(document.querySelector('[role="dialog"]')).toBeNull()

    await w.findAll('.task__open')[0]?.trigger('click')
    await settle()

    const sheet = document.querySelector('[role="dialog"]')
    expect(sheet).not.toBeNull()
    const text = sheet?.textContent ?? ''
    expect(text).toContain('样例·每日签到')
    // 规则由后端拼 —— 容错次数这类信息前端拼不出来
    expect(text).toContain('连续完成 5 次，中断即清零')
    expect(text).toContain('9月30日 23:59')
    expect(text).toContain('积分188')
    expect(text).toContain('红包8')
    /*
     * 🔴 详情里也不能出现「领取」：任务达标即自动发奖，
     * 状态机里没有 CLAIMED。这条断言和列表那条是一对。
     */
    expect(text).not.toContain('领取')
  })

  it('「去完成」是行按钮的兄弟节点，点它不会连带打开详情', async () => {
    const w = mount(Tasks, { global })
    await settle()
    /*
     * 交互元素套交互元素是无效 HTML，而且点链接会连带触发外层按钮。
     * ProductCard 当初就是这么踩的（收藏按钮曾套在 RouterLink 里）。
     */
    expect(w.find('.task__open .task__go').exists()).toBe(false)
    expect(w.find('.task__go').exists()).toBe(true)
  })

  it('单档任务不画阶梯 —— 它的奖励在右侧那行摘要里', async () => {
    const w = mount(Tasks, { global })
    await settle()
    // 三个任务里只有第一个是多档的
    expect(w.findAll('.ladder')).toHaveLength(1)
  })

  it('状态文案直接用后端给的，前端不做映射', async () => {
    const w = mount(Tasks, { global })
    await settle()
    // 「已发奖」是后端的 statusText，前端没有任何状态码到中文的表
    expect(w.html()).toContain('已发奖')
    expect(w.html()).toContain('未开始')
  })

  it('没有 actionUrl 的任务不画「去完成」—— 点了没去处的按钮比没有更糟', async () => {
    const w = mount(Tasks, { global })
    await settle()
    const links = w.findAll('.task__go')
    // 桩里只有「逛商城」配了 actionUrl
    expect(links).toHaveLength(1)
    expect(links[0]?.attributes('href')).toBe('/mall')
  })
})

describe('首页路由', () => {
  it('三个 tab 都继承了 meta.tab，底部导航在子路由上照常显示', async () => {
    const real = (await import('@/router')).default
    for (const name of ['feed', 'mall', 'tasks', 'activities']) {
      expect(real.resolve({ name }).meta.tab).toBe(true)
      // 默认需要登录：公开页要显式开 anonymous，这三个都不该有
      expect(real.resolve({ name }).meta.anonymous).toBeUndefined()
    }
    /*
     * redirect 只在导航时生效，resolve() 只查表不跟 redirect —— 所以这两条必须
     * 真的导一次航。导航会被登录守卫拦到 /login，而 query.redirect 里留着的
     * 正是 redirect 之后的最终目标，用它断言。
     */
    await real.push('/')
    expect(real.currentRoute.value.query.redirect).toBe('/')

    // 老的底部「优惠」入口不该 404
    await real.push('/promo')
    expect(real.currentRoute.value.query.redirect).toBe('/activities')
  })
})
