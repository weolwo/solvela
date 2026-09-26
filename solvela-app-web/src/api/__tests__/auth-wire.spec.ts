import type { AxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { login, register } from '../auth'

/**
 * 登录 / 注册**发出去的字段名**。
 *
 * <h3>这条测试对应一个真实的断链</h3>
 * 2026-09-09 后端把登录注册的入参从 `phone` / `password` 改成
 * `identity` / `credential`，并加了 `loginType` / `registerType`
 *（理由见 MemberLoginRequest 的注释：「继续叫 phone 但有时候放的是邮箱」
 * 是一个迟早会骗到人的字段名）。
 *
 * <p>前端没跟上，于是**登录和注册全都是 400**「请输入手机号或邮箱」——
 * 而 TypeScript 一个字都不会说，因为两边的类型定义各在一个仓里。
 *
 * <h3>所以这里断言的是「发出去的那个 body」，不是函数签名</h3>
 * 类型能保证调用方按 `LoginPayload` 传参，保证不了 `LoginPayload`
 * 长得和后端一样。跨仓契约唯一能在本仓钉住的，就是最终打在网线上的字段名 ——
 * 改名时这条会红，而红了才有人去看后端。
 */

const request = vi.hoisted(() => vi.fn())

vi.mock('../http', () => ({
  request,
  requestVoid: vi.fn(() => Promise.resolve()),
}))

/** 后端真实会下发的形状：小值 Long 是数字 */
const LOGIN_REPLY = {
  // cookie 模式下服务端不回令牌原文（它在 HttpOnly cookie 里）
  expiresIn: 2_592_000,
  member: {
    memberId: 1000000001,
    memberName: 'sv1000000001',
    nickname: '会员',
    avatarFileId: null,
    gender: 0,
  },
}

function sentBody(): Record<string, unknown> {
  const config = request.mock.calls[0]?.[0] as AxiosRequestConfig | undefined
  expect(config, '压根没发出请求').toBeDefined()
  return (config?.data ?? {}) as Record<string, unknown>
}

beforeEach(() => {
  request.mockReset()
  request.mockResolvedValue(LOGIN_REPLY)
})

describe('登录', () => {
  it('🔴 发的是 identity / credential / loginType，不是 phone / password', async () => {
    await login(
      { loginType: 'PHONE_PASSWORD', identity: '13800138000', credential: 'abcd1234' },
      true,
    )

    expect(sentBody()).toMatchObject({
      loginType: 'PHONE_PASSWORD',
      identity: '13800138000',
      credential: 'abcd1234',
    })
    expect(sentBody()).not.toHaveProperty('phone')
    expect(sentBody()).not.toHaveProperty('password')
  })

  it('不传 loginType 时兜底成手机号密码 —— 与后端 typeOrDefault 一致', async () => {
    await login({ identity: '13800138000', credential: 'abcd1234' }, true)

    expect(sentBody().loginType).toBe('PHONE_PASSWORD')
  })

  it('调用方给的 deviceType 覆盖默认值，而不是被默认值盖掉', async () => {
    await login({ identity: '13800138000', credential: 'abcd1234', deviceType: 'APP' }, true)

    // 默认值写在展开之前正是为了这个：`{ ...默认, ...payload }`。
    // 顺序写反的话，调用方永远改不动 deviceType，而且没有任何报错
    expect(sentBody().deviceType).toBe('APP')
  })

  it('落地的 memberId 是字符串 —— 后端下发的是数字', async () => {
    const result = await login({ identity: '13800138000', credential: 'abcd1234' }, true)

    expect(result.member.memberId).toBe('1000000001')
  })
})

describe('注册', () => {
  it('🔴 发的是 identity / registerType，并且带上 smsCode', async () => {
    await register({
      registerType: 'PHONE_PASSWORD',
      identity: '13800138000',
      password: 'abcd1234',
      smsCode: '123456',
    })

    expect(sentBody()).toMatchObject({
      registerType: 'PHONE_PASSWORD',
      identity: '13800138000',
      password: 'abcd1234',
      smsCode: '123456',
    })
    expect(sentBody()).not.toHaveProperty('phone')
  })

  it('邮箱注册：带 emailCode，password 可以不填', async () => {
    await register({ registerType: 'EMAIL_CODE', identity: 'a@example.com', emailCode: '123456' })

    expect(sentBody()).toMatchObject({
      registerType: 'EMAIL_CODE',
      identity: 'a@example.com',
      emailCode: '123456',
    })
    expect(sentBody().password).toBeUndefined()
  })
})

describe('🔴 令牌走 HttpOnly cookie', () => {
  it('登录要求 cookie 下发，并把「记住我」原样交给服务端', async () => {
    await login({ identity: 'a@example.com', credential: '123456' }, false)

    // useCookie 丢了的话，服务端会照旧把令牌放进响应体 —— HttpOnly 等于白做
    expect(sentBody()).toMatchObject({ useCookie: true, remember: false })
  })

  it('注册同样要求 cookie 下发', async () => {
    await register({ registerType: 'EMAIL_CODE', identity: 'a@example.com', emailCode: '123456' })

    expect(sentBody()).toMatchObject({ useCookie: true })
  })

  it('结果里没有令牌字段 —— 前端不该持有它', async () => {
    const result = await login({ identity: 'a@example.com', credential: '123456' }, true)

    expect(result).not.toHaveProperty('accessToken')
  })
})
