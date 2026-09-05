import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { CommodityQuery } from '@/api/mall'
import type { Address, AddressInput } from '@/api/address'
import { toId, type Id } from '@/types/contract'

import RedeemView from '../RedeemView.vue'

/*
 * 资产接口 2026-09-05 已接通真实后端，jsdom 里发不出去。
 * 本用例验的是兑换页的算账与拦截逻辑，余额是它的<b>输入</b>，
 * 所以在这里把它钉死 —— 比让测试依赖某个桩里的数字更清楚，
 * 也不会因为后端改了样例数据就红。
 */
/* mock 工厂里不能写 import() 类型注解（eslint），先在这里起个别名 */
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type MallModule = typeof import('@/api/mall')
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type AddressModule = typeof import('@/api/address')

vi.mock('@/api/assets', () => ({
  fetchAssets: () =>
    Promise.resolve([
      { assetType: 'SCORE', label: '积分', amount: '12345.67', currency: true, frozen: false },
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

/*
 * 地址簿 2026-09-05 已接通真实后端。这里保留一份可变的内存实现 ——
 * 「删除要点两次」那条用例要看到删除<b>确实</b>让列表少一行。
 */
vi.mock('@/api/address', async (importOriginal) => {
  const actual = await importOriginal<AddressModule>()
  const fixtures = await import('@/testing/fixtures')
  let list = [...fixtures.ADDRESSES]
  return {
    ...actual,
    fetchAddresses: () =>
      Promise.resolve([...list].sort((a, b) => Number(b.isDefault) - Number(a.isDefault))),
    fetchAddress: (id: Id) => {
      const found = list.find((a) => a.id === id)
      return found === undefined ? Promise.reject(new Error('不存在')) : Promise.resolve(found)
    },
    createAddress: (input: AddressInput) => {
      const created: Address = {
        ...fixtures.ADDRESSES[0]!,
        ...input,
        id: toId('8100'),
        isDefault: false,
      }
      list = [...list, created]
      return Promise.resolve(created)
    },
    updateAddress: (id: Id, input: AddressInput) => {
      const updated: Address = { ...list.find((a) => a.id === id)!, ...input }
      list = list.map((a) => (a.id === id ? updated : a))
      return Promise.resolve(updated)
    },
    deleteAddress: (id: Id) => {
      list = list.filter((a) => a.id !== id)
      return Promise.resolve()
    },
    setDefaultAddress: (id: Id) => {
      list = list.map((a) => ({ ...a, isDefault: a.id === id }))
      return Promise.resolve()
    },
  }
})

setActivePinia(createPinia())

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/me', name: 'mine', component: { template: '<div/>' } },
    { path: '/address', name: 'address-list', component: { template: '<div/>' } },
    // 页面是下面 mount() 挂的，这条路由只负责提供 params 与 query
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

async function mountRedeem(path: string) {
  await router.push(path)
  await router.isReady()
  const w = mount(RedeemView, { global })
  await settle()
  return w
}

/**
 * 7002：实物、积分 45,000 + 现金 ¥299、两组规格。
 * SKU 80021 = 午夜蓝 38，库存 8。桩里积分余额是 12,345.67。
 */
const FULL = '/redeem/7002?sku=80021&qty=1'

describe('RedeemView', () => {
  it('把 query 里的 SKU 还原成可读摘要，并显示件数', async () => {
    const w = await mountRedeem(FULL)
    const html = w.html()
    expect(html).toContain('样例·Apple Watch Series 6')
    expect(html).toContain('颜色 午夜蓝 · 尺码 38')
    expect(html).toContain('× 1')
    // 账单按件数算：45,000 × 1 积分 + ¥299.00 × 1
    expect(html).toContain('45,000 积分')
    expect(html).toContain('¥299.00')
  })

  it('件数按库存与限兑收窄 —— query 里写 999 也不算数', async () => {
    // 午夜蓝38 库存 8，但每日限兑还剩 2
    const w = await mountRedeem('/redeem/7002?sku=80021&qty=999')
    expect(w.html()).toContain('× 2')
    expect(w.html()).toContain('90,000 积分')
  })

  it('query 里的 SKU 是假的或已无货，就当没选并拦住兑换', async () => {
    // 80024 = 曜石黑 42，库存 0
    const w = await mountRedeem('/redeem/7002?sku=80024&qty=1')
    expect(w.find('.bar__hint').text()).toContain('请先回上一页选择规格')
    expect(w.find('.sv-btn').attributes('disabled')).toBeDefined()

    const bogus = await mountRedeem('/redeem/7002?sku=NOPE&qty=1')
    expect(bogus.find('.bar__hint').text()).toContain('请先回上一页选择规格')
  })

  it('积分不够时说清还差多少', async () => {
    const w = await mountRedeem(FULL)
    // 余额 12,345.67，要 45,000 —— 差 32,654.33，取整显示 32,654
    expect(w.find('.bar__hint').text()).toContain('积分不足，还差 32,654 积分')
    expect(w.find('.sv-btn').attributes('disabled')).toBeDefined()
  })

  it('实物带默认地址，虚拟商品根本没有地址那一段', async () => {
    const physical = await mountRedeem(FULL)
    expect(physical.html()).toContain('收货地址')
    // fetchAddresses 把默认地址排在最前，页面取第 0 条
    expect(physical.html()).toContain('张三')
    expect(physical.html()).toContain('138****8000')

    // 7008 是 COUPON，无规格商品的 SKU id 是 '9' + commodityId
    const virtual = await mountRedeem('/redeem/7008?sku=97008&qty=1')
    expect(virtual.html()).not.toContain('收货地址')
    expect(virtual.html()).toContain('1,000 积分')
  })

  it('虚拟商品积分够，能兑，兑完显示订单号与到账', async () => {
    const w = await mountRedeem('/redeem/7008?sku=97008&qty=1')
    expect(w.find('.bar__hint').exists()).toBe(false)
    expect(w.find('.sv-btn').attributes('disabled')).toBeUndefined()

    await w.find('.sv-btn').trigger('click')
    await settle()
    expect(w.find('.done__title').text()).toBe('兑换成功，权益已到账')
    expect(w.find('.done__no').text()).toContain('DEMO')
  })

  it('去地址簿挑地址时，商品与 SKU、件数都带过去', async () => {
    const w = await mountRedeem(FULL)
    await w.find('.addr').trigger('click')
    await flushPromises()
    const query = router.currentRoute.value.query
    expect(router.currentRoute.value.name).toBe('address-list')
    expect(query.pick).toBe('1')
    expect(query.commodity).toBe('7002')
    // 选好的 SKU 与件数不能在挑地址的路上丢掉
    expect(query.sku).toBe('80021')
    expect(query.qty).toBe('1')
  })
})
