import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Address, AddressInput } from '@/api/address'
import { toId, type Id } from '@/types/contract'

import AddressFormView from '../AddressFormView.vue'
import AddressListView from '../AddressListView.vue'

/*
 * 地址簿 2026-09-05 已接通真实后端。这里保留一份可变的内存实现 ——
 * 「删除要点两次」那条用例要看到删除<b>确实</b>让列表少一行。
 */
/* mock 工厂里不能写 import() 类型注解（eslint），先在这里起个别名 */
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type AddressModule = typeof import('@/api/address')

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
    { path: '/address', name: 'address-list', component: { template: '<div/>' } },
    { path: '/address/new', name: 'address-new', component: { template: '<div/>' } },
    { path: '/address/:id', name: 'address-edit', component: { template: '<div/>' } },
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

async function mountAt(component: unknown, path: string) {
  await router.push(path)
  await router.isReady()
  const w = mount(component as never, { global })
  await settle()
  return w
}

describe('AddressListView', () => {
  it('列出地址，默认那条排最前并带标', async () => {
    const w = await mountAt(AddressListView, '/address')
    const rows = w.findAll('.row')
    expect(rows.length).toBeGreaterThanOrEqual(2)
    expect(rows[0]?.text()).toContain('张三')
    expect(rows[0]?.text()).toContain('默认')
    // 列表下发的是脱敏手机号，不是明文
    expect(w.html()).toContain('138****8000')
    expect(w.html()).not.toContain('13800008000')
  })

  it('删除要点两次：第一次只是变成确认', async () => {
    const w = await mountAt(AddressListView, '/address')
    const before = w.findAll('.row').length
    const del = w.findAll('.row')[1]?.findAll('.op--danger')[0]
    await del?.trigger('click')
    expect(del?.text()).toBe('确定删除？')
    expect(w.findAll('.row')).toHaveLength(before)

    await del?.trigger('click')
    await settle()
    expect(w.findAll('.row')).toHaveLength(before - 1)
  })

  it('挑选模式下点一条就带着地址回兑换页，SKU 与件数原样带回', async () => {
    const w = await mountAt(AddressListView, '/address?pick=1&commodity=7002&sku=80021&qty=2')
    await w.findAll('.row__main')[0]?.trigger('click')
    await flushPromises()
    const route = router.currentRoute.value
    expect(route.name).toBe('redeem')
    expect(route.params.id).toBe('7002')
    expect(route.query.address).toBe('8001')
    expect(route.query.sku).toBe('80021')
    expect(route.query.qty).toBe('2')
    // 挑选相关的两个参数不该留下
    expect(route.query.pick).toBeUndefined()
    expect(route.query.commodity).toBeUndefined()
  })
})

describe('AddressFormView', () => {
  it('必填项没填就提交，逐项报错', async () => {
    const w = await mountAt(AddressFormView, '/address/new')
    await w.find('form').trigger('submit')
    await flushPromises()
    const html = w.html()
    expect(html).toContain('请填写收件人')
    expect(html).toContain('请填写手机号')
    expect(html).toContain('省、市、区都要填')
    expect(html).toContain('请填写详细地址')
  })

  it('手机号格式不对要拦住', async () => {
    const w = await mountAt(AddressFormView, '/address/new')
    const inputs = w.findAll('input')
    await inputs[1]?.setValue('12345')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(w.html()).toContain('手机号格式不对')
  })

  it('编辑时回填地址，但手机号留空（脱敏值不能提交回去）', async () => {
    const w = await mountAt(AddressFormView, '/address/8001')
    const inputs = w.findAll('input')
    expect((inputs[0]?.element as HTMLInputElement).value).toBe('张三')
    expect((inputs[1]?.element as HTMLInputElement).value).toBe('')
    expect(w.html()).toContain('留空表示不修改手机号')
    // 手机号留空在编辑态是合法的，不该报错
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(w.html()).not.toContain('请填写手机号')
  })
})
