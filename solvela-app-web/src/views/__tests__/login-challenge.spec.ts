import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { ApiError } from '@/api/errors'

import LoginView from '../LoginView.vue'

/**
 * 观察档设备的登录二次验证（凭票版）。
 *
 * <h3>流程</h3>
 * 输密码点登录 → 服务端回 DEVICE_VERIFICATION_REQUIRED，details 里带一张凭票 →
 * 页面亮出验证码栏并**自动凭票发码** → 用户输码 → **凭票验码**，通过即登录成功。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 第二步不再调 login、不再交密码 —— 密码在签票时已验过；</li>
 *   <li>🔴 发码凭票，不再去匿名的短信接口要码（那个口子已经关了），邮箱登录也能走通；</li>
 *   <li>票失效（CHALLENGE_EXPIRED）→ 回到输密码那一步，而不是让人对着「获取验证码」反复点。</li>
 * </ul>
 */

const loginApi = vi.hoisted(() => vi.fn())
const sendLoginChallengeCode = vi.hoisted(() => vi.fn(() => Promise.resolve('138****8000')))
const verifyLoginChallenge = vi.hoisted(() => vi.fn())

/* mock 工厂里不能写 import() 类型注解（eslint），先在这里起个别名 */
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type AuthModule = typeof import('@/api/auth')

vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<AuthModule>()),
  login: loginApi,
  sendLoginChallengeCode,
  verifyLoginChallenge,
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

const OK = {
  expiresIn: 3600,
  member: {
    memberId: '1000000001',
    memberName: 'sv1000000001',
    nickname: '会员',
    avatarFileId: null,
    gender: 0,
  },
}

const TICKET = 'lc_ticket_1'

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

/** 输手机号密码点登录，服务端回「还差一步」并附上凭票 */
async function reachChallenge(w: Wrapper) {
  loginApi.mockRejectedValueOnce(
    new ApiError(
      'DEVICE_VERIFICATION_REQUIRED',
      '为了你的账号安全，请输入验证码后继续',
      'tr-1',
      401,
      {
        challengeTicket: TICKET,
      },
    ),
  )
  await fieldOf(w, '手机号').setValue('13800138000')
  await fieldOf(w, '密码').setValue('abcd1234')
  await w.find('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  loginApi.mockReset()
  loginApi.mockResolvedValue(OK)
  sendLoginChallengeCode.mockReset()
  sendLoginChallengeCode.mockResolvedValue('138****8000')
  verifyLoginChallenge.mockReset()
  verifyLoginChallenge.mockResolvedValue(OK)
})

describe('观察档二次验证（凭票）', () => {
  it('正常设备上，验证码那一栏根本不出现，也不发码', async () => {
    const w = await mountPage()

    expect(
      inputOf(w, '验证码'),
      '默认就摆着的话，绝大多数用户会以为每次登录都要验一道码',
    ).toBeUndefined()
    expect(sendLoginChallengeCode).not.toHaveBeenCalled()
  })

  it('🔴 被要求二次验证 → 验证码栏冒出来，并自动凭票发码；手机号密码原样留着', async () => {
    const w = await mountPage()

    await reachChallenge(w)

    expect(inputOf(w, '验证码'), '不亮出输入框，用户就没有任何办法继续').toBeDefined()
    expect(sendLoginChallengeCode).toHaveBeenCalledWith(TICKET)
    expect(fieldOf(w, '手机号').element.value).toBe('13800138000')
    expect(w.text(), '要告诉用户码寄到了哪，否则他不知道该去看短信还是邮箱').toContain(
      '138****8000',
    )
  })

  it('🔴 提交走凭票验码：不再调 login、不再交密码', async () => {
    const w = await mountPage()
    await reachChallenge(w)
    loginApi.mockClear()

    await fieldOf(w, '验证码').setValue('123456')
    await w.find('form').trigger('submit')
    await flushPromises()

    expect(verifyLoginChallenge).toHaveBeenCalledWith(TICKET, '123456', true)
    expect(
      loginApi,
      '第二步再调一次 login 就又要把密码交一遍 —— 凭票正是为了免掉这一步',
    ).not.toHaveBeenCalled()
  })

  it('码错（401 BAD_CREDENTIALS）挂在验证码栏上，不当成密码错', async () => {
    const w = await mountPage()
    await reachChallenge(w)
    verifyLoginChallenge.mockRejectedValueOnce(
      new ApiError('BAD_CREDENTIALS', '验证码错误，请重新获取', null, 401),
    )

    await fieldOf(w, '验证码').setValue('000000')
    await w.find('form').trigger('submit')
    await flushPromises()

    expect(inputOf(w, '验证码'), '码错了票还在，栏不能收起来').toBeDefined()
    expect(w.text()).toContain('验证码错误')
  })

  it('🔴 票失效（CHALLENGE_EXPIRED）→ 验证码栏收起，再点登录重新走一遍拿新票', async () => {
    const w = await mountPage()
    await reachChallenge(w)
    verifyLoginChallenge.mockRejectedValueOnce(
      new ApiError('CHALLENGE_EXPIRED', '验证已过期，请重新登录', null, 401),
    )

    await fieldOf(w, '验证码').setValue('123456')
    await w.find('form').trigger('submit')
    await flushPromises()

    expect(
      inputOf(w, '验证码'),
      '票都没了，还留着验证码栏只会让人反复点「获取验证码」',
    ).toBeUndefined()
    expect(w.text()).toContain('验证已过期')

    loginApi.mockClear()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(loginApi, '再提交应当重新走「密码登录」拿一张新票').toHaveBeenCalledTimes(1)
  })

  it('🔴 换了手机号 → 这一整轮作废，验证码栏收起来', async () => {
    const w = await mountPage()
    await reachChallenge(w)

    await fieldOf(w, '手机号').setValue('13900139000')
    await flushPromises()

    expect(
      inputOf(w, '验证码'),
      '拿 A 号的票去登 B 号，得到的只会是一句莫名其妙的错误',
    ).toBeUndefined()
  })

  it('正常登录一次都不受影响：只调 login，不带任何二次验证字段', async () => {
    const w = await mountPage()

    await fieldOf(w, '手机号').setValue('13800138000')
    await fieldOf(w, '密码').setValue('abcd1234')
    await w.find('form').trigger('submit')
    await flushPromises()

    expect(loginApi).toHaveBeenCalledTimes(1)
    expect(loginApi.mock.calls[0]?.[0]).not.toHaveProperty('verificationCode')
    expect(verifyLoginChallenge).not.toHaveBeenCalled()
  })
})
