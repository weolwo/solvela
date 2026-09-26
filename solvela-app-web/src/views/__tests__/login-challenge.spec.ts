import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { ApiError } from '@/api/errors'

import LoginView from '../LoginView.vue'

/**
 * 观察档设备的二次验证。
 *
 * <h3>这条路径做错的表现是「用户永远登不进去，而服务端一切正常」</h3>
 * 服务端回 `DEVICE_VERIFICATION_REQUIRED` 的意思是**密码已经验过了，只是还差一步**。
 * 把它当成登录失败处理的话，用户被退回登录页从头再来 —— 而他每次都会走到同一个地方，
 * 报障时只会说「我密码没错但就是登不上」。
 *
 * <p>所以这里钉三条：**验证码栏要冒出来**、**手机号和密码要原样留着**、
 * **补上码之后要能真的登进去**。
 */

const sendSmsCode = vi.hoisted(() => vi.fn(() => Promise.resolve()))
const loginApi = vi.hoisted(() => vi.fn())

/* mock 工厂里不能写 import() 类型注解（eslint），先在这里起个别名 */
/* eslint-disable-next-line @typescript-eslint/consistent-type-imports */
type AuthModule = typeof import('@/api/auth')

vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<AuthModule>()),
  sendSmsCode,
  login: loginApi,
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'feed', component: { template: '<div/>' } },
    { path: '/login', name: 'login', component: { template: '<div/>' } },
    { path: '/register', name: 'register', component: { template: '<div/>' } },
    /*
     * 登录页上「忘记密码」是一个 RouterLink。
     * 🔴 少了这条路由，整个 LoginView 会在渲染时抛 "No match for password-reset" ——
     * 而报错发生在 RouterLink 里，看起来像是路由库坏了。
     * 路由名是页面之间的契约，测试里的假路由表也得跟着它走。
     */
    { path: '/password/reset', name: 'password-reset', component: { template: '<div/>' } },
  ],
})

const OK = {
  accessToken: 'mb_x',
  expiresIn: 3600,
  member: {
    memberId: '1000000001',
    memberName: 'sv1000000001',
    nickname: '会员',
    avatarFileId: null,
    gender: 0,
  },
}

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

/** 走一遍「提交 → 被要求二次验证」。 */
async function reachChallenge(w: Wrapper) {
  loginApi.mockRejectedValueOnce(
    new ApiError(
      'DEVICE_VERIFICATION_REQUIRED',
      '为了你的账号安全，请输入验证码后继续',
      'tr-1',
      401,
    ),
  )
  await fieldOf(w, '手机号').setValue('13800138000')
  await fieldOf(w, '密码').setValue('abcd1234')
  await w.find('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  sendSmsCode.mockReset()
  sendSmsCode.mockResolvedValue(undefined)
  loginApi.mockReset()
  loginApi.mockResolvedValue(OK)
})

describe('观察档二次验证', () => {
  it('正常设备上，验证码那一栏根本不出现', async () => {
    const w = await mountPage()

    expect(
      inputOf(w, '短信验证码'),
      '默认就摆着的话，绝大多数用户会以为每次登录都要验一道码',
    ).toBeUndefined()
  })

  it('🔴 被要求二次验证 → 验证码栏冒出来，手机号和密码【原样留着】', async () => {
    const w = await mountPage()

    await reachChallenge(w)

    expect(inputOf(w, '短信验证码'), '不亮出输入框，用户就没有任何办法继续').toBeDefined()
    expect(
      fieldOf(w, '手机号').element.value,
      '清空重来的话，用户每次都会走到同一个地方，而他不知道为什么',
    ).toBe('13800138000')
    expect(fieldOf(w, '密码').element.value).toBe('abcd1234')
  })

  it('获取验证码用的是 LOGIN 场景 —— 与注册那条码互不相干', async () => {
    const w = await mountPage()
    await reachChallenge(w)

    const btn = w.findAll('button').find((b) => b.text().includes('获取验证码'))
    await btn?.trigger('click')
    await flushPromises()

    expect(sendSmsCode).toHaveBeenCalledWith('LOGIN', '13800138000')
  })

  it('🔴 补上码之后要真的带上去，而且能登进去', async () => {
    const w = await mountPage()
    await reachChallenge(w)

    await fieldOf(w, '短信验证码').setValue('123456')
    await w.find('form').trigger('submit')
    await flushPromises()

    // api 层的 login(payload, remember)：remember 由服务端决定下发持久还是会话 cookie
    expect(loginApi).toHaveBeenLastCalledWith(
      expect.objectContaining({ identity: '13800138000', verificationCode: '123456' }),
      expect.any(Boolean),
    )
  })

  it('进到这一步之后的 401 说的是【验证码】不对，挂在验证码栏上', async () => {
    const w = await mountPage()
    await reachChallenge(w)
    loginApi.mockRejectedValueOnce(
      new ApiError('BAD_CREDENTIALS', '验证码错误，请重新获取', 'tr-2', 401),
    )

    await fieldOf(w, '短信验证码').setValue('000000')
    await w.find('form').trigger('submit')
    await flushPromises()

    const fieldMsgs = w.findAll('.sv-field__msg--error').map((n) => n.text())
    expect(fieldMsgs).toContain('验证码错误，请重新获取')
  })

  it('🔴 换了手机号 → 这一整轮作废，验证码栏收起来', async () => {
    const w = await mountPage()
    await reachChallenge(w)
    expect(inputOf(w, '短信验证码')).toBeDefined()

    await fieldOf(w, '手机号').setValue('13900139000')
    await flushPromises()

    expect(
      inputOf(w, '短信验证码'),
      '那道码是发给上一个号的。留着它，用户会拿 A 号的码去登 B 号，' +
        '得到一句「验证码错误」而完全不知道自己错在哪',
    ).toBeUndefined()
  })

  it('正常登录一次都不受影响：不带 verificationCode', async () => {
    const w = await mountPage()
    await fieldOf(w, '手机号').setValue('13800138000')
    await fieldOf(w, '密码').setValue('abcd1234')

    await w.find('form').trigger('submit')
    await flushPromises()

    expect(loginApi.mock.calls[0]?.[0]).toMatchObject({ verificationCode: undefined })
  })
})
