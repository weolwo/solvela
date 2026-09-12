import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { ApiError } from '@/api/errors'

import PasswordResetView from '../PasswordResetView.vue'

/**
 * 忘记密码。
 *
 * <h3>默认走手机号，这一条不是随手定的</h3>
 * 手机号是这个系统<b>注册的默认身份</b>。默认成邮箱的话，
 * 绝大多数用户打开这一页第一眼看到的是一个自己填不了的框 ——
 * 而他正处在「进不去、有点慌」的状态。
 *
 * <h3>🔴 切换通道要把上一条码清掉</h3>
 * 不清的话：用户给邮箱发了码，改用手机号，那个码还躺在框里，
 * 提交时收到「验证码错误」—— 而他明明刚收到过一条。
 * 这类 bug 不会报错，只会让人觉得「这功能坏了」。
 *
 * <h3>成功之后要显示「已在 N 台设备上退出登录」</h3>
 * 点忘记密码最常见的原因之一就是「我怀疑号被人动过」，
 * 那个数字正是他要的答案。
 */

const resetPassword = vi.hoisted(() => vi.fn())
const sendSmsCode = vi.hoisted(() => vi.fn(() => Promise.resolve()))
const sendEmailCode = vi.hoisted(() => vi.fn(() => Promise.resolve()))

/* mock 工厂里不能写 import() 类型注解（eslint），先在这里起个别名 */
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type AuthModule = typeof import('@/api/auth')

vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<AuthModule>()),
  resetPassword,
  sendSmsCode,
  sendEmailCode,
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'feed', component: { template: '<div/>' } },
    { path: '/login', name: 'login', component: { template: '<div/>' } },
    { path: '/password/reset', name: 'password-reset', component: { template: '<div/>' } },
  ],
})

async function mountPage() {
  await router.push('/password/reset')
  await router.isReady()
  const w = mount(PasswordResetView, { global: { plugins: [router] } })
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

async function switchToEmail(w: Wrapper) {
  await w
    .findAll('button')
    .find((b) => b.text() === '邮箱')
    ?.trigger('click')
  await flushPromises()
}

beforeEach(() => {
  resetPassword.mockReset()
  resetPassword.mockResolvedValue({ revokedSessions: 3 })
  sendSmsCode.mockReset()
  sendSmsCode.mockResolvedValue(undefined)
  sendEmailCode.mockReset()
  sendEmailCode.mockResolvedValue(undefined)
})

describe('通道', () => {
  it('🔴 默认是手机号 —— 它才是注册的默认身份', async () => {
    const w = await mountPage()

    expect(inputOf(w, '注册手机号')).toBeDefined()
    expect(inputOf(w, '账号邮箱')).toBeUndefined()
  })

  it('切到邮箱之后，发码走邮箱那条', async () => {
    const w = await mountPage()
    await switchToEmail(w)
    await fieldOf(w, '账号邮箱').setValue('a@example.com')

    await w
      .findAll('button')
      .find((b) => b.text().includes('获取验证码'))
      ?.trigger('click')
    await flushPromises()

    expect(sendEmailCode).toHaveBeenCalledWith('RESET_PASSWORD', 'a@example.com')
    expect(sendSmsCode).not.toHaveBeenCalled()
  })

  it('手机号那条走短信', async () => {
    const w = await mountPage()
    await fieldOf(w, '注册手机号').setValue('13800138000')

    await w
      .findAll('button')
      .find((b) => b.text().includes('获取验证码'))
      ?.trigger('click')
    await flushPromises()

    expect(sendSmsCode).toHaveBeenCalledWith('RESET_PASSWORD', '13800138000')
  })

  it('🔴 切换通道 → 已填的验证码要清掉，冷却也要解除', async () => {
    const w = await mountPage()
    await fieldOf(w, '注册手机号').setValue('13800138000')
    await w
      .findAll('button')
      .find((b) => b.text().includes('获取验证码'))
      ?.trigger('click')
    await flushPromises()
    await fieldOf(w, '短信验证码').setValue('123456')

    await switchToEmail(w)

    expect(
      fieldOf(w, '邮箱验证码').element.value,
      '留着上一条通道的码，用户提交时会收到「验证码错误」—— 而他明明刚收到过一条',
    ).toBe('')
    expect(w.findAll('button').some((b) => b.text() === '获取验证码')).toBe(true)
  })
})

describe('提交', () => {
  it('🔴 带上 resetType 与 identity，不是 email', async () => {
    const w = await mountPage()
    await fieldOf(w, '注册手机号').setValue('13800138000')
    await fieldOf(w, '短信验证码').setValue('123456')
    await fieldOf(w, '新密码').setValue('abcd1234')
    await fieldOf(w, '再次输入新密码').setValue('abcd1234')

    await w.find('form').trigger('submit')
    await flushPromises()

    expect(resetPassword).toHaveBeenCalledWith({
      resetType: 'SMS_CODE',
      identity: '13800138000',
      code: '123456',
      newPassword: 'abcd1234',
    })
  })

  it('两次密码不一致 → 本地就拦下，不发请求', async () => {
    const w = await mountPage()
    await fieldOf(w, '注册手机号').setValue('13800138000')
    await fieldOf(w, '短信验证码').setValue('123456')
    await fieldOf(w, '新密码').setValue('abcd1234')
    await fieldOf(w, '再次输入新密码').setValue('abcd9999')

    await w.find('form').trigger('submit')
    await flushPromises()

    expect(resetPassword).not.toHaveBeenCalled()
    expect(w.text()).toContain('两次输入的密码不一致')
  })

  it('🔴 成功之后显示「已在 N 台设备上退出登录」', async () => {
    const w = await mountPage()
    await fieldOf(w, '注册手机号').setValue('13800138000')
    await fieldOf(w, '短信验证码').setValue('123456')
    await fieldOf(w, '新密码').setValue('abcd1234')
    await fieldOf(w, '再次输入新密码').setValue('abcd1234')

    await w.find('form').trigger('submit')
    await flushPromises()

    expect(w.text()).toContain('密码已重置')
    expect(w.text(), '点忘记密码最常见的原因就是「我怀疑号被人动过」，这个数字正是答案').toContain(
      '3',
    )
  })

  it('验证码错 → 挂在验证码框上，不是丢进表单底部', async () => {
    resetPassword.mockRejectedValue(new ApiError('BAD_CREDENTIALS', '验证码错误', 'tr-1', 401))
    const w = await mountPage()
    await fieldOf(w, '注册手机号').setValue('13800138000')
    await fieldOf(w, '短信验证码').setValue('000000')
    await fieldOf(w, '新密码').setValue('abcd1234')
    await fieldOf(w, '再次输入新密码').setValue('abcd1234')

    await w.find('form').trigger('submit')
    await flushPromises()

    expect(w.findAll('.sv-field__msg--error').map((n) => n.text())).toContain('验证码错误')
  })
})
