import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { CommodityQuery } from '@/api/mall'
import type { Id } from '@/types/contract'

import ProductView from '../ProductView.vue'

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

/*
 * 商城读路径 2026-09-05 已接通真实后端，jsdom 里发不出去。
 * 样例数据集中在 @/testing/fixtures —— 五个 spec 共用一份，
 * 免得「午夜蓝 38 库存是 8 还是 6」这种细节在文件之间漂。
 */
/* mock 工厂里不能写 import() 类型注解（eslint），先在这里起个别名 */
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type MallModule = typeof import('@/api/mall')

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
    { path: '/login', name: 'login', component: { template: '<div/>' } },
    /*
     * 路由表里放占位组件而不是 ProductView 本体：页面是下面 mount() 挂的，
     * 这条路由只负责提供 params.id 和 fullPath。
     * （另外 .ts 里 value-import 一个 .vue，在 eslint 的类型解析下会退化成 any。）
     */
    { path: '/product/:id', name: 'product', component: { template: '<div/>' } },
    { path: '/redeem/:id', name: 'redeem', component: { template: '<div/>' } },
  ],
})

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

/** 7002 是桩里那块 Apple Watch，规格配得最全 */
async function mountDetail(id = '7002') {
  await router.push(`/product/${id}`)
  await router.isReady()
  const w = mount(ProductView, { global })
  await settle()
  return w
}

describe('ProductView', () => {
  it('渲染标题、价格、库存与评分', async () => {
    const w = await mountDetail()
    const html = w.html()
    expect(html).toContain('样例·Apple Watch Series 6')
    // 7002 是「积分 + 现金」那件，两半都要写出来
    expect(html).toContain('45,000 积分 + ¥299.00')
    // 划线位是「值多少钱」（original_price 是现金），不是划掉的积分
    expect(html).toContain('价值 ¥3,199.00')
    expect(html).toContain('现货 24 件')
    // 限兑来自 limit_period / limit_count / remainingCount
    expect(html).toContain('每日限兑 2 件，还可兑 2 件')
    expect(html).toContain('兑换须知')
  })

  it('没有图集时不画滑不动的圆点', async () => {
    const w = await mountDetail()
    expect(w.find('.sv-carousel').exists()).toBe(false)
    expect(w.find('.media__initial').exists()).toBe(true)
  })

  it('规格分组是从 SKU 列表推出来的', async () => {
    const w = await mountDetail()
    const groups = w.findAll('.attr')
    // 前两组来自 sku_attrs 的键，顺序按第一个 SKU 的键顺序
    expect(groups[0]?.find('.attr__title').text()).toBe('颜色')
    expect(groups[1]?.find('.attr__title').text()).toBe('尺码')
    // 值按首次出现去重
    expect(groups[0]?.findAll('.opt').map((b) => b.text())).toEqual(['午夜蓝', '曜石黑', '珍珠白'])
  })

  it('规格没选全时按钮置灰，并说清差哪一项', async () => {
    const w = await mountDetail()
    expect(w.find('.bar__hint').text()).toBe('请选择颜色')
    expect(w.find('.sv-btn').attributes('disabled')).toBeDefined()

    const colorOpts = w.findAll('.attr')[0]?.findAll('.opt') ?? []
    await colorOpts[0]?.trigger('click')
    expect(w.find('.bar__hint').text()).toBe('请选择尺码')

    const sizeOpts = w.findAll('.attr')[1]?.findAll('.opt') ?? []
    await sizeOpts[0]?.trigger('click')
    expect(w.find('.bar__hint').exists()).toBe(false)
    expect(w.find('.sv-btn').attributes('disabled')).toBeUndefined()
  })

  it('整个颜色都无货时展示但选不中', async () => {
    const w = await mountDetail()
    const colorOpts = w.findAll('.attr')[0]?.findAll('.opt') ?? []
    // 珍珠白的两个 SKU 库存都是 0
    const white = colorOpts[2]
    expect(white?.classes()).toContain('opt--out')
    expect(white?.attributes('disabled')).toBeDefined()
    await white?.trigger('click')
    expect(white?.classes()).not.toContain('opt--on')
  })

  it('可选性跟着已选的其它规格收窄', async () => {
    const w = await mountDetail()
    // 曜石黑42 库存 0：先选曜石黑，42 就该变成不可选
    const colorOpts = w.findAll('.attr')[0]?.findAll('.opt') ?? []
    await colorOpts[1]?.trigger('click')
    const sizeOpts = w.findAll('.attr')[1]?.findAll('.opt') ?? []
    expect(sizeOpts[0]?.classes()).not.toContain('opt--out')
    expect(sizeOpts[1]?.classes()).toContain('opt--out')
  })

  it('件数受库存与限兑两头夹', async () => {
    const w = await mountDetail()
    const colorOpts = w.findAll('.attr')[0]?.findAll('.opt') ?? []
    const sizeOpts = w.findAll('.attr')[1]?.findAll('.opt') ?? []
    await colorOpts[0]?.trigger('click')
    await sizeOpts[0]?.trigger('click')
    // 午夜蓝38 库存 8，但每日限兑还剩 2 —— 取小的那个
    const plus = w.findAll('.qty__btn')[1]
    await plus?.trigger('click')
    expect(w.find('.qty__value').text()).toBe('2')
    await plus?.trigger('click')
    expect(w.find('.qty__value').text()).toBe('2')
  })

  it('未登录点兑换跳登录，并带上回来的路径', async () => {
    const w = await mountDetail()
    const colorOpts = w.findAll('.attr')[0]?.findAll('.opt') ?? []
    const sizeOpts = w.findAll('.attr')[1]?.findAll('.opt') ?? []
    await colorOpts[0]?.trigger('click')
    await sizeOpts[0]?.trigger('click')

    await w.find('.sv-btn').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/product/7002')
  })

  it('无规格商品也有一行 SKU（skuAttrs 为空对象），所以推不出任何分组', async () => {
    const w = await mountDetail('7008')
    expect(w.html()).toContain('1,000 积分')
    const titles = w.findAll('.attr__title').map((t) => t.text())
    expect(titles).toContain('兑换件数')
    expect(titles).not.toContain('颜色')
    // 虚拟商品不用选规格，直接可兑
    expect(w.find('.sv-btn').attributes('disabled')).toBeUndefined()
  })

  it('认不出的商品也有兜底详情，不白屏', async () => {
    const w = await mountDetail('nope')
    expect(w.find('.title').text()).toContain('Redmi')
    expect(w.findAll('.attr').length).toBeGreaterThan(0)
  })
})
