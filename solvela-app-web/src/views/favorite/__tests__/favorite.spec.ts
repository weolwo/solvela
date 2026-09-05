import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { CommodityQuery } from '@/api/mall'
import type { Id } from '@/types/contract'

import { fetchFavorites, toggleFavorite } from '@/api/mall'
import { toId } from '@/types/contract'
import FavoriteView from '../FavoriteView.vue'

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
    { path: '/mall', name: 'mall', component: { template: '<div/>' } },
    { path: '/favorites', name: 'favorites', component: { template: '<div/>' } },
    // ProductCard 整张卡是去详情页的链接，路由表缺了会 No match
    { path: '/product/:id', name: 'product', component: { template: '<div/>' } },
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

describe('FavoriteView', () => {
  it('只列出收藏过的商品', async () => {
    const w = mount(FavoriteView, { global })
    await settle()
    // 桩里初始只有 7002 是收藏状态
    expect(w.findAll('.card')).toHaveLength(1)
    expect(w.html()).toContain('样例·Apple Watch Series 6')
    expect(w.html()).toContain('1 件')
  })

  it('在别处收藏的商品，这一页看得到 —— 桩不是各存各的', async () => {
    await toggleFavorite(toId('7005'), true)
    const list = await fetchFavorites()
    expect(list.map((c) => c.commodityId)).toContain('7005')

    const w = mount(FavoriteView, { global })
    await settle()
    expect(w.html()).toContain('纯棉圆领 T 恤')

    // 收拾干净，免得影响同文件里后面的用例
    await toggleFavorite(toId('7005'), false)
  })

  it('取消收藏后卡片留在原地，只是心形变空 —— 点错了还能点回来', async () => {
    const w = mount(FavoriteView, { global })
    await settle()
    const fav = w.find('.card__fav')
    expect(fav.classes()).toContain('card__fav--on')

    await fav.trigger('click')
    expect(w.find('.card__fav').classes()).not.toContain('card__fav--on')
    // 卡片没消失
    expect(w.findAll('.card')).toHaveLength(1)

    /*
     * 必须等上一次收藏请求回来再点第二次：请求在飞的时候按钮是禁用的
     *（useFavorites 的 pending 守卫），不等就点等于什么也没点。
     */
    await settle()
    await w.find('.card__fav').trigger('click')
    await settle()
    expect(w.find('.card__fav').classes()).toContain('card__fav--on')
  })

  it('一件都没有时给一条出路', async () => {
    await toggleFavorite(toId('7002'), false)
    const w = mount(FavoriteView, { global })
    await settle()
    expect(w.findAll('.card')).toHaveLength(0)
    expect(w.find('.page__cta').text()).toBe('去商城逛逛')

    await toggleFavorite(toId('7002'), true)
  })
})
