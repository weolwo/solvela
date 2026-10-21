import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/errors'
import { hasSessionHint, setSessionHint } from '@/utils/session-hint'

import { useAuthStore } from '../auth'

/**
 * 登录态（HttpOnly cookie 版）的启动。
 *
 * <p>令牌在 cookie 里，前端读不到，「登着没有」要问服务端。这组用例钉住两件事：
 * <ul>
 *   <li>匿名访客不白问 —— 没有「上次登着」的提示就一个请求都不发；</li>
 * *   <li>网络抖一下不能被当成「已退出」。</li>
 * </ul>
 */

const api = vi.hoisted(() => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
}))

vi.mock('@/api/auth', () => api)
vi.mock('@/api/device', () => ({ ensureDevice: vi.fn(() => Promise.resolve(true)) }))

const MEMBER = { memberId: '1', memberName: 'sv1', nickname: '会员', avatarFileId: null, gender: 0 }

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  Object.values(api).forEach((fn) => fn.mockReset())
  setActivePinia(createPinia())
})

describe('启动恢复', () => {
  it('没有「上次登着」的提示 → 一个请求都不发，匿名访客不白吃 401', async () => {
    const auth = useAuthStore()
    await auth.restore()

    expect(api.fetchMe).not.toHaveBeenCalled()
    expect(auth.isLoggedIn).toBe(false)
  })

  it('有提示 → 问一次服务端，拿到会员就是登着', async () => {
    setSessionHint()
    api.fetchMe.mockResolvedValue(MEMBER)
    const auth = useAuthStore()

    await auth.restore()

    expect(auth.isLoggedIn).toBe(true)
  })

  it('并发调用只问一次 —— 路由守卫每次导航都会调它', async () => {
    setSessionHint()
    api.fetchMe.mockResolvedValue(MEMBER)
    const auth = useAuthStore()

    await Promise.all([auth.restore(), auth.restore(), auth.restore()])

    expect(api.fetchMe).toHaveBeenCalledTimes(1)
  })

  it('网络失败：不登录，但提示留着，下次还会再问 —— 不因为一次抖动就当成已退出', async () => {
    setSessionHint()
    api.fetchMe.mockRejectedValue(new ApiError('NETWORK', '网络连接失败', null, null))
    const auth = useAuthStore()

    await auth.restore()

    expect(auth.isLoggedIn).toBe(false)
    expect(hasSessionHint()).toBe(true)
  })
})

describe('登录 / 退出', () => {
  it('登录把「记住我」交给服务端，并记下「上次登着」', async () => {
    api.login.mockResolvedValue({ expiresIn: 1, member: MEMBER })
    const auth = useAuthStore()

    await auth.login({ identity: 'a@example.com', credential: '123456' }, false)

    expect(api.login).toHaveBeenCalledWith(
      { identity: 'a@example.com', credential: '123456' },
      false,
    )
    expect(auth.isLoggedIn).toBe(true)
    expect(hasSessionHint()).toBe(true)
  })

  it('退出：服务端清 cookie，本地清提示 —— 服务端失败也照样清', async () => {
    api.login.mockResolvedValue({ expiresIn: 1, member: MEMBER })
    api.logout.mockRejectedValue(new Error('network'))
    const auth = useAuthStore()
    await auth.login({ identity: 'a@example.com', credential: '123456' }, true)

    await auth.logout()

    expect(auth.isLoggedIn).toBe(false)
    expect(hasSessionHint()).toBe(false)
  })
})
