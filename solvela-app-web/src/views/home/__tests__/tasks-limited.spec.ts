import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { ApiError } from '@/api/errors'

import Tasks from '../Tasks.vue'

/**
 * 签到被限流（网关 SIGN 场景）时：按钮按服务端给的秒数置灰、显示倒计时，到 0 恢复；提示用服务端的原话。
 * 兑换、抽奖、领取、充值、支付那几颗按钮是同一个做法（useRetryAfter），这里拿签到当代表钉住整条路。
 */

const signApi = vi.hoisted(() => vi.fn())

vi.mock('@/api/task', () => ({
  fetchTasks: () => Promise.resolve([]),
  fetchSignedToday: () => Promise.resolve(false),
  sign: signApi,
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', name: 'feed', component: { template: '<div/>' } }],
})

async function mountTasks() {
  await router.push('/')
  await router.isReady()
  const w = mount(Tasks, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

function signButton(w: Awaited<ReturnType<typeof mountTasks>>) {
  const found = w.findAll('button').find((b) => /签到|后可再试/.test(b.text()))
  expect(found, '没找到签到按钮').toBeDefined()
  return found!
}

describe('签到被限流', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    signApi.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('🔴 429 → 按钮置灰并倒计时，提示说服务端的原话；到 0 恢复可点', async () => {
    signApi.mockRejectedValueOnce(
      new ApiError('OPERATION_LIMITED', '操作过于频繁，请 60 秒后再试', null, 429, { retryAfterSeconds: 60 }),
    )
    const w = await mountTasks()

    await signButton(w).trigger('click')
    await flushPromises()

    expect(signButton(w).text()).toContain('01:00 后可再试')
    expect(signButton(w).attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('操作过于频繁，请 60 秒后再试')

    await vi.advanceTimersByTimeAsync(60_000)
    await flushPromises()
    expect(signButton(w).text()).toBe('签到')
    expect(signButton(w).attributes('disabled')).toBeUndefined()
  })

  it('别的失败不倒计时，按钮立刻可再点', async () => {
    signApi.mockRejectedValueOnce(new ApiError('INTERNAL', '服务开小差了', null, 500))
    const w = await mountTasks()

    await signButton(w).trigger('click')
    await flushPromises()

    expect(signButton(w).text()).toBe('签到')
    expect(signButton(w).attributes('disabled')).toBeUndefined()
    expect(w.text()).toContain('签到失败，请稍后再试')
  })
})
