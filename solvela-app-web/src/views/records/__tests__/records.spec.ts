import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { OrderItem, RecordItem } from '@/api/records'

import ExchangeRecordsView from '../ExchangeRecordsView.vue'
import PromoRecordsView from '../PromoRecordsView.vue'

/**
 * 我的记录：兑换与优惠两页。
 *
 * <h3>这里守的两条都是「用户会不会去投诉」</h3>
 * ① <b>履约失败时必须说清积分还在不在。</b>看到「发放失败」，用户第一个念头
 *    就是这个。失败不退积分（东西还欠着他），不说的话他会以为积分白扣了。
 * ② <b>风控拦截不能说破。</b>说成「未通过」，和「驳回」同一句话 ——
 *    告诉他「单笔超限」等于告诉他下次怎么绕。
 */

const ORDERS: OrderItem[] = [
  {
    orderNo: 'M20260905120000123ABCDEF',
    commodityName: '样例·限量T恤',
    coverUrl: 'http://127.0.0.1:1024/support/file/public/mall_commodity/demo.png',
    specs: ['颜色：曜石黑', '尺码：L'],
    quantity: 2,
    cost: '45,000 积分 + ¥299.00',
    statusText: '发放失败',
    status: 'FAILED',
    hint: '积分未退回，我们会尽快重新为你发放，也可联系客服',
    createTime: '2026-09-05 12:00:00',
  },
  {
    orderNo: 'M20260904090000456GHIJKL',
    commodityName: '样例·优惠券',
    coverUrl: null,
    specs: [],
    quantity: 1,
    cost: '5,000 积分',
    statusText: '已完成',
    status: 'DONE',
    hint: null,
    createTime: '2026-09-04 09:00:00',
  },
]

/* 优惠记录：一条到账、一条「未通过」（底层是风控拦截，但那个词不能出现） */
const PROMOS: RecordItem[] = [
  {
    recordId: '9001' as RecordItem['recordId'],
    title: '新人礼包',
    statusText: '已到账',
    status: 'DONE',
    amount: '5000',
    createTime: '2026-09-05 10:00:00',
  },
  {
    recordId: '9002' as RecordItem['recordId'],
    title: '积分',
    statusText: '未通过',
    status: 'FAILED',
    amount: '100',
    createTime: '2026-09-05 09:00:00',
  },
]

vi.mock('@/api/records', async (importOriginal) => {
  /* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
  const actual = await importOriginal<typeof import('@/api/records')>()
  return {
    ...actual,
    fetchExchangeRecords: () => Promise.resolve(ORDERS),
    fetchPromoRecords: () => Promise.resolve(PROMOS),
  }
})

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div />' } }],
})

const global = { plugins: [router] }

beforeEach(() => {
  setActivePinia(createPinia())
})

async function settle(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
}

describe('兑换记录', () => {
  it('渲染商品名、规格、对价与单号', async () => {
    const w = mount(ExchangeRecordsView, { global })
    await settle()
    const text = w.text()
    expect(text).toContain('样例·限量T恤')
    expect(text).toContain('颜色：曜石黑')
    // 对价是服务端拼好的整句：积分是整数、现金才有小数
    expect(text).toContain('45,000 积分 + ¥299.00')
    // 纯积分那单不该出现「+ ¥0.00」—— 那截只会让人以为还要再付钱
    expect(text).toContain('5,000 积分')
    expect(text).not.toContain('¥0.00')
    expect(text).toContain('M20260905120000123ABCDEF')
  })

  it('🔴 履约失败时说清积分还在不在', async () => {
    const w = mount(ExchangeRecordsView, { global })
    await settle()
    /*
     * 用户看到「发放失败」第一个念头就是「我的积分呢」。
     * 履约失败不退积分（东西还欠着他，不是没买），不说的话
     * 他会以为积分白扣了，然后去投诉。
     */
    expect(w.text()).toContain('积分未退回')
  })

  it('有封面就渲染 <img>，没有就画首字占位', async () => {
    const w = mount(ExchangeRecordsView, { global })
    await settle()
    const imgs = w.findAll('.order__img:not(.order__img--empty)')
    expect(imgs).toHaveLength(1)
    expect(imgs[0]?.attributes('src')).toBe(
      'http://127.0.0.1:1024/support/file/public/mall_commodity/demo.png',
    )
    // 第二单没配图 —— 画占位块，不拼一个 URL 去试
    expect(w.findAll('.order__img--empty')).toHaveLength(1)
  })

  it('全部状态都出，不只出成功的', async () => {
    const w = mount(ExchangeRecordsView, { global })
    await settle()
    // 只出成功的等于把「我兑的东西呢」藏起来，而那正是用户点进来最想知道的
    expect(w.text()).toContain('发放失败')
    expect(w.text()).toContain('已完成')
  })
})

describe('优惠记录', () => {
  it('渲染标题、面值与状态', async () => {
    const w = mount(PromoRecordsView, { global })
    await settle()
    const text = w.text()
    expect(text).toContain('新人礼包')
    expect(text).toContain('已到账')
    expect(text).toContain('5,000')
  })

  it('🔴 一个字都不出现「提案」', async () => {
    const w = mount(PromoRecordsView, { global })
    await settle()
    /*
     * 「提案」是运营视角的词（要过审批）。用户不需要知道
     * 他的奖励要过两道审批，更不该在界面上看到这个词。
     */
    expect(w.text()).not.toContain('提案')
  })

  it('🔴 风控拦截说成「未通过」，不说破拦截原因', async () => {
    const w = mount(PromoRecordsView, { global })
    await settle()
    const text = w.text()
    expect(text).toContain('未通过')
    // 说破等于告诉用户下次怎么绕过去
    expect(text).not.toContain('风控')
    expect(text).not.toContain('超限')
    expect(text).not.toContain('拦截')
  })
})
