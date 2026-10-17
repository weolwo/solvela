import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import SecurityPromptDialog from '../SecurityPromptDialog.vue'

/**
 * 「刚才是你登录的吗？」弹窗。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 **两颗按钮不等重**：「不是我」要二次确认 —— 它会让所有设备下线并冻结兑换，
 *       和「是我」一样显眼的话，一半的人会随手点错；</li>
 *   <li>🔴 **拉不到就不弹**，而且不显示任何错误 —— 用户没主动做任何事，
 *       不该看到一条失败提示；更不能反过来「拉不到就拦住他」；</li>
 *   <li>答完「不是我」要指路到重置密码；</li>
 *   <li>多条排队，一次只问一个。</li>
 * </ul>
 */

const fetchSecurityPrompts = vi.hoisted(() => vi.fn())
const answerSecurityPrompt = vi.hoisted(() => vi.fn(() => Promise.resolve()))

/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type SecurityModule = typeof import('@/api/security')

vi.mock('@/api/security', async (importOriginal) => ({
  ...(await importOriginal<SecurityModule>()),
  fetchSecurityPrompts,
  answerSecurityPrompt,
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'feed', component: { template: '<div/>' } },
    { path: '/password/reset', name: 'password-reset', component: { template: '<div/>' } },
  ],
})

const PROMPT = {
  promptId: 'sp_1',
  deviceType: 'APP' as const,
  location: '深圳',
  loginTime: 1760001234000,
}

async function mountDialog() {
  await router.push('/')
  await router.isReady()
  const w = mount(SecurityPromptDialog, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

/**
 * 弹窗 teleport 到 body，所以断言都去 document 上找 —— 包装器里是空的。
 * （踩过一次：对着 `dialogText()` 断言，全都是空字符串。）
 */
function dialogText(): string {
  return document.body.textContent ?? ''
}

function buttonByText(text: string): HTMLButtonElement | undefined {
  return [...document.body.querySelectorAll('button')].find((b) => b.textContent?.trim() === text)
}

async function click(text: string): Promise<void> {
  const button = buttonByText(text)
  expect(button, `没有文案为「${text}」的按钮`).toBeDefined()
  button!.click()
  await flushPromises()
}

beforeEach(() => {
  fetchSecurityPrompts.mockReset()
  fetchSecurityPrompts.mockResolvedValue([])
  answerSecurityPrompt.mockReset()
  answerSecurityPrompt.mockResolvedValue(undefined)
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('刚才是你吗', () => {
  it('没有待确认时什么都不弹（绝大多数时候就是这样）', async () => {
    await mountDialog()

    expect(dialogText()).toBe('')
  })

  it('有待确认 → 弹出来，把时间、地点、设备都说清', async () => {
    fetchSecurityPrompts.mockResolvedValue([PROMPT])
    await mountDialog()

    expect(dialogText()).toContain('刚才是你登录的吗')
    expect(dialogText()).toContain('深圳')
    expect(dialogText()).toContain('手机 App')
  })

  it('🔴 拉不到就不弹，而且不显示任何错误', async () => {
    fetchSecurityPrompts.mockRejectedValue(new Error('network down'))
    await mountDialog()

    expect(dialogText(), '一次接口抖动不该变成一个挡在用户面前的东西').toBe('')
  })

  it('点「是我」直接上报，不再问第二遍', async () => {
    fetchSecurityPrompts.mockResolvedValue([PROMPT])
    await mountDialog()

    await click('是我')

    expect(answerSecurityPrompt).toHaveBeenCalledWith('sp_1', true)
    expect(dialogText()).toBe('')
  })

  it('🔴 点「不是我」先二次确认，而且确认页要把后果说全', async () => {
    fetchSecurityPrompts.mockResolvedValue([PROMPT])
    await mountDialog()

    await click('不是我')

    expect(
      answerSecurityPrompt,
      '它会让所有设备下线并冻结兑换，不该点一下就执行',
    ).not.toHaveBeenCalled()
    expect(dialogText()).toContain('所有设备下线')
    expect(dialogText()).toContain('兑换')
    expect(dialogText()).toContain('重置密码')
  })

  it('确认「不是我」→ 上报，并指路到重置密码', async () => {
    fetchSecurityPrompts.mockResolvedValue([PROMPT])
    await mountDialog()
    await click('不是我')

    await click('确认，不是我')

    expect(answerSecurityPrompt).toHaveBeenCalledWith('sp_1', false)
    expect(dialogText()).toContain('已处理')

    await click('去重置密码')
    expect(router.currentRoute.value.name).toBe('password-reset')
  })

  it('多条排队，一次只问一个，并说明还剩几条', async () => {
    fetchSecurityPrompts.mockResolvedValue([PROMPT, { ...PROMPT, promptId: 'sp_2' }])
    await mountDialog()

    expect(dialogText()).toContain('还有 1 条需要确认')

    await click('是我')

    expect(dialogText()).toContain('刚才是你登录的吗')
    expect(dialogText(), '只剩一条时不该再说「还有」').not.toContain('还有 1 条')
  })

  it('上报失败时把这一条收起来，不让用户对着一个点不动的按钮反复点', async () => {
    fetchSecurityPrompts.mockResolvedValue([PROMPT])
    answerSecurityPrompt.mockRejectedValue(new Error('boom'))
    await mountDialog()

    await click('是我')

    expect(dialogText()).toBe('')
  })
})
