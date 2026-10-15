import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { ApiError } from '@/api/errors'

import LoginView from '../LoginView.vue'

/**
 * 登录被风控挡下时，页面该给什么。
 *
 * <h3>三种挡法，三种给法 —— 判据是「用户此刻还能不能做点什么」</h3>
 * <ul>
 *   <li><b>PASSWORD_COOLDOWN</b> —— 只锁了密码这条通道，还有验证码登录和找回密码可走。
 *       所以弹窗 + 倒计时 + 把那几条出路摆成按钮；</li>
 *   <li><b>OPERATION_LIMITED</b> —— 唯一的出路是等。所以**不弹窗**，只在按钮上走倒计时
 *       （这一档的对象主要是脚本，弹窗除了多一次点击什么也没给）；</li>
 *   <li><b>LOGIN_VERIFY_UNAVAILABLE</b> —— 自己什么也做不了。弹窗说清要找客服，
 *       而且 🔴 **绝不能出现「去绑定」** —— 此刻没有登录态，对面只证明了「知道密码」，
 *       让他绑一个联系方式就是给账号加一条他自己的找回通道。</li>
 * </ul>
 */

const loginApi = vi.hoisted(() => vi.fn())
const sendLoginChallengeCode = vi.hoisted(() => vi.fn(() => Promise.resolve('a***@x.com')))

/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type AuthModule = typeof import('@/api/auth')

vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<AuthModule>()),
  login: loginApi,
  sendLoginChallengeCode,
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'feed', component: { template: '<div/>' } },
    { path: '/login', name: 'login', component: { template: '<div/>' } },
    { path: '/register', name: 'register', component: { template: '<div/>' } },
    { path: '/password/reset', name: 'password-reset', component: { template: '<div/>' } },
  ],
})

async function mountPage() {
  await router.push('/login')
  await router.isReady()
  const w = mount(LoginView, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

type Wrapper = Awaited<ReturnType<typeof mountPage>>

function inputOf(w: Wrapper, placeholder: string) {
  return w.findAll('input').find((i) => i.attributes('placeholder') === placeholder)
}

function fieldOf(w: Wrapper, placeholder: string) {
  const found = inputOf(w, placeholder)
  expect(found, `没有 placeholder 为「${placeholder}」的输入框`).toBeDefined()
  return found!
}

/** 弹窗 teleport 到 body，所以要去 document 上找 */
function dialogText(): string {
  return document.body.textContent ?? ''
}

function buttonLabels(): string[] {
  return [...document.body.querySelectorAll('[role="alertdialog"] button')].map(
    (b) => b.textContent?.trim() ?? '',
  )
}

async function submitPassword(w: Wrapper, error: ApiError) {
  loginApi.mockRejectedValueOnce(error)
  await fieldOf(w, '手机号').setValue('13800138000')
  await fieldOf(w, '密码').setValue('abcd1234')
  await w.find('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers({ shouldAdvanceTime: true })
  loginApi.mockReset()
  sendLoginChallengeCode.mockReset()
  sendLoginChallengeCode.mockResolvedValue('a***@x.com')
})

afterEach(() => {
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('密码阶梯冷却', () => {
  const cooldown = () =>
    new ApiError('PASSWORD_COOLDOWN', '密码错误次数过多，请稍后再试', 'tr-1', 429, {
      retryAfterSeconds: 300,
      alternatives: ['CODE_LOGIN', 'PASSWORD_RESET'],
    })

  it('🔴 弹窗带倒计时，并把服务端给的出路摆成按钮', async () => {
    const w = await mountPage()

    await submitPassword(w, cooldown())

    expect(dialogText(), '只展示一句「请稍后再试」的话，用户只会盯着等').toContain(
      '密码错误次数过多',
    )
    expect(dialogText(), '要有准确的倒计时，不是「稍后」').toContain('05:00')
    expect(buttonLabels()).toContain('用验证码登录')
    expect(buttonLabels()).toContain('找回密码')
  })

  it('🔴 出路由服务端决定：手机号登录拿到空数组时，只剩「知道了」', async () => {
    const w = await mountPage()

    await submitPassword(
      w,
      new ApiError('PASSWORD_COOLDOWN', '密码错误次数过多，请稍后再试', null, 429, {
        retryAfterSeconds: 60,
        alternatives: [],
      }),
    )

    expect(
      buttonLabels(),
      '手机号找回密码也要短信，短信没接通时给出口等于让用户撞一个发不出码的页面',
    ).toEqual(['知道了'])
  })

  it('登录按钮在倒计时期间点不动，并且说明还要等多久', async () => {
    const w = await mountPage()

    await submitPassword(w, cooldown())

    const submit = w.find('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
    expect(submit.text(), '按钮点不动时，原因必须写在按钮上').toContain('05:00')
  })

  it('🔴 点「用验证码登录」→ 切到邮箱验证码，并且倒计时要停掉', async () => {
    const w = await mountPage()
    await submitPassword(w, cooldown())

    const codeLogin = [...document.body.querySelectorAll('[role="alertdialog"] button')].find(
      (b) => b.textContent?.trim() === '用验证码登录',
    )
    expect(codeLogin).toBeDefined()
    ;(codeLogin as HTMLButtonElement).click()
    await flushPromises()

    expect(inputOf(w, '邮箱'), '没切过去的话，这颗按钮就是个摆设').toBeDefined()
    expect(inputOf(w, '邮箱验证码')).toBeDefined()
    expect(
      w.find('button[type="submit"]').attributes('disabled'),
      '被冷却的只有密码这条通道 —— 留着倒计时，用户照我们说的切过来了却还是点不动',
    ).toBeUndefined()
  })
})

describe('被限流', () => {
  it('🔴 不弹窗，只在按钮上倒计时 —— 这一档唯一的出路就是等', async () => {
    const w = await mountPage()

    await submitPassword(
      w,
      new ApiError('OPERATION_LIMITED', '当前网络登录尝试过于频繁，请 2 分钟后重试', null, 429, {
        retryAfterSeconds: 90,
      }),
    )

    expect(document.body.querySelector('[role="alertdialog"]')).toBeNull()
    expect(w.find('button[type="submit"]').text()).toContain('01:30')
  })

  it('服务端没给秒数时不编一个出来', async () => {
    const w = await mountPage()

    await submitPassword(
      w,
      new ApiError('OPERATION_LIMITED', '操作过于频繁，请稍后再试', null, 429),
    )

    expect(
      w.find('button[type="submit"]').attributes('disabled'),
      '倒计时从一个编出来的数字开始走，走到 0 用户以为能点了，结果又是一次 429',
    ).toBeUndefined()
  })
})

describe('没有可用的核身通道', () => {
  it('🔴 弹窗指向客服，而且绝不出现「去绑定」', async () => {
    const w = await mountPage()

    await submitPassword(
      w,
      new ApiError('LOGIN_VERIFY_UNAVAILABLE', '暂时无法验证你的身份，请联系客服', 'tr-2', 403),
    )

    expect(dialogText()).toContain('无法验证你的身份')
    expect(dialogText()).toContain('联系客服')
    expect(
      buttonLabels().join(''),
      '此刻对面只证明了「知道密码」。给一颗「去绑定」等于让他给别人的账号加一条自己的找回通道',
    ).not.toContain('绑定')
  })
})

describe('凭据错时的「最后一次机会」', () => {
  it('服务端给了 attemptsBeforeCooldown 才提醒，而且提醒里有别的出路', async () => {
    const w = await mountPage()

    await submitPassword(
      w,
      new ApiError('BAD_CREDENTIALS', '手机号或密码错误', null, 401, {
        attemptsBeforeCooldown: 1,
      }),
    )

    expect(w.text()).toContain('手机号或密码错误')
    expect(w.text()).toContain('再错一次将暂时停用密码登录')
  })

  it('没给这个数就什么也不多说 —— 一直说等于把阶梯的形状告诉对面', async () => {
    const w = await mountPage()

    await submitPassword(w, new ApiError('BAD_CREDENTIALS', '手机号或密码错误', null, 401))

    expect(w.text()).not.toContain('再错一次')
  })
})

describe('验证码通道的文案', () => {
  it('🔴 按服务端给的 channel 说，不按登录方式猜', async () => {
    const w = await mountPage()
    // 手机号登录，但服务端换了通道 / 退回到邮箱
    sendLoginChallengeCode.mockImplementation(() => new Promise(() => {}))

    await submitPassword(
      w,
      new ApiError('DEVICE_VERIFICATION_REQUIRED', '请输入验证码后继续', null, 401, {
        challengeTicket: 'lc_x',
        channel: 'EMAIL',
      }),
    )

    expect(w.text(), '照登录方式猜的话这里会说「手机」，而用户永远等不到那条短信').toContain(
      '验证码会发到你的邮箱',
    )
  })
})
