import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import SessionsView from '../SessionsView.vue'

/**
 * 「我的登录设备」里的「这不是我」。
 *
 * <h3>这里钉住的是「别让用户误触」和「别让他不知道后果」</h3>
 * 这个按钮会**冻结兑换、加地址、充话费**到改密为止。所以：
 * <ul>
 *   <li>🔴 和「下线」分开，而且要二次确认 —— 清理旧设备是常见操作，
 *       合成一个按钮的话每次清理都会把自己的账号冻住；</li>
 *   <li>🔴 确认框里要把后果说全（下线 + 冻结 + 要改密），不是一句「确定吗」；</li>
 *   <li>🔴 成功之后要指路到重置密码 —— 那是恢复的唯一方式。
 *       不说的话，他下一次兑换时会撞上一个看不懂的「账号处于保护状态」。</li>
 * </ul>
 */

const fetchSessions = vi.hoisted(() => vi.fn())
const reportSession = vi.hoisted(() => vi.fn(() => Promise.resolve()))
const revokeSession = vi.hoisted(() => vi.fn(() => Promise.resolve()))
const revokeOtherSessions = vi.hoisted(() => vi.fn(() => Promise.resolve()))

/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type SessionModule = typeof import('@/api/session')

vi.mock('@/api/session', async (importOriginal) => ({
  ...(await importOriginal<SessionModule>()),
  fetchSessions,
  reportSession,
  revokeSession,
  revokeOtherSessions,
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'feed', component: { template: '<div/>' } },
    { path: '/sessions', name: 'sessions', component: { template: '<div/>' } },
    { path: '/password/reset', name: 'password-reset', component: { template: '<div/>' } },
  ],
})

const CURRENT = {
  sessionId: 's-current',
  deviceType: 'H5' as const,
  deviceId: 'dev-current',
  ip: '1.1.1.1',
  region: null,
  loginTime: 1760000000000,
  current: true,
}

const OTHER = {
  sessionId: 's-other',
  deviceType: 'APP' as const,
  deviceId: 'dev-other',
  ip: '2.2.2.2',
  region: null,
  loginTime: 1760001234000,
  current: false,
}

async function mountPage() {
  await router.push('/sessions')
  await router.isReady()
  const w = mount(SessionsView, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

type Wrapper = Awaited<ReturnType<typeof mountPage>>

function buttonByText(w: Wrapper, text: string) {
  return w.findAll('button').find((b) => b.text().trim() === text)
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchSessions.mockReset()
  fetchSessions.mockResolvedValue([CURRENT, OTHER])
  reportSession.mockReset()
  reportSession.mockResolvedValue(undefined)
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('这不是我', () => {
  it('🔴 只出现在【其它】设备上：本机那一条不该有它，也不该有「下线」', async () => {
    const w = await mountPage()

    const cards = w.findAll('li')
    expect(cards).toHaveLength(2)
    expect(
      cards[0]!.text(),
      '本机点下去会当场把自己踢出这个页面，而他多半只是想处理别人那台',
    ).not.toContain('这不是我')
    expect(cards[1]!.text()).toContain('这不是我')
  })

  it('🔴 点一下不执行，先确认，而且确认框要把后果说全', async () => {
    const w = await mountPage()

    await buttonByText(w, '这不是我')!.trigger('click')

    expect(
      reportSession,
      '点一下就执行的话，清理旧设备会顺手把自己的账号冻住',
    ).not.toHaveBeenCalled()
    const text = w.text()
    expect(text).toContain('下线')
    expect(text, '不说冻结，用户下次兑换时会以为系统坏了').toContain('兑换')
    expect(text).toContain('重置密码')
  })

  it('确认后带着那一条的设备号与登录时间上报 —— 服务端靠它们定位「那一次登录」', async () => {
    const w = await mountPage()
    await buttonByText(w, '这不是我')!.trigger('click')

    await buttonByText(w, '确认，这不是我')!.trigger('click')
    await flushPromises()

    expect(reportSession).toHaveBeenCalledWith(OTHER)
  })

  it('🔴 成功之后指路到重置密码 —— 那是恢复兑换的唯一方式', async () => {
    const w = await mountPage()
    await buttonByText(w, '这不是我')!.trigger('click')
    await buttonByText(w, '确认，这不是我')!.trigger('click')
    await flushPromises()

    expect(w.text()).toContain('已处理')
    const go = buttonByText(w, '去重置密码')
    expect(go, '只刷新列表了事的话，用户不知道自己还要做一件事').toBeDefined()

    await go!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('password-reset')
  })

  it('取消就什么也不做', async () => {
    const w = await mountPage()
    await buttonByText(w, '这不是我')!.trigger('click')

    await buttonByText(w, '取消')!.trigger('click')
    await flushPromises()

    expect(reportSession).not.toHaveBeenCalled()
    expect(w.text()).not.toContain('确认，这不是我')
  })
})
