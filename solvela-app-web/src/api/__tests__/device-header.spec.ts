import type { AxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import http, { DEVICE_REGISTER_URL, configureHttp, request } from '../http'

/**
 * 凭证不经过请求头：会话与设备令牌都在 HttpOnly cookie 里，同源请求由浏览器自动带上。
 *
 * <h3>为什么要钉「不带」</h3>
 * 以前拦截器从 localStorage 取令牌塞进 Authorization / X-Device-Token 头。
 * 改成 cookie 之后，哪怕有人把那段代码加回来、而取值的地方换成别的存储，
 * 都意味着令牌又回到了页面脚本读得到的地方 —— HttpOnly 就白做了。
 *
 * <h3>🔴 领设备身份那条请求自己不能等设备</h3>
 * 它会走同一个拦截器。如果它也去等设备身份，等的就是它自己 ——
 * 页面表现为**所有请求永久挂起**，连报错都没有。
 */

/** 把 adapter 换掉，请求不出网，同时拿到最终发出去的 config */
function captureAdapter(): { seen: AxiosRequestConfig[]; restore: () => void } {
  const seen: AxiosRequestConfig[] = []
  const original = http.defaults.adapter
  http.defaults.adapter = (config) => {
    seen.push(config)
    return Promise.resolve({ data: {}, status: 200, statusText: 'OK', headers: {}, config })
  }
  return {
    seen,
    restore: () => {
      // exactOptionalPropertyTypes 下 `= undefined` 不合法，要真的把这个键删掉
      if (original === undefined) {
        delete http.defaults.adapter
      } else {
        http.defaults.adapter = original
      }
    },
  }
}

function headerOf(config: AxiosRequestConfig | undefined, name: string): unknown {
  return (config?.headers as Record<string, unknown> | undefined)?.[name]
}

let ensureDevice: ReturnType<typeof vi.fn<() => Promise<void>>>

beforeEach(() => {
  ensureDevice = vi.fn<() => Promise<void>>(() => Promise.resolve())
  configureHttp({ onLoginRequired: () => {}, ensureDevice })
})

describe('凭证', () => {
  it('🔴 不带 Authorization，也不带 X-Device-Token —— 它们在 HttpOnly cookie 里', async () => {
    const { seen, restore } = captureAdapter()
    try {
      await request({ url: '/auth/me', method: 'POST' })
    } finally {
      restore()
    }

    expect(headerOf(seen[0], 'Authorization')).toBeUndefined()
    expect(headerOf(seen[0], 'X-Device-Token')).toBeUndefined()
  })

  it('发请求之前先等设备身份落地 —— 匿名的登录、注册也要带着设备 cookie', async () => {
    const order: string[] = []
    ensureDevice.mockImplementation(() => {
      order.push('ensure')
      return Promise.resolve()
    })
    const original = http.defaults.adapter
    http.defaults.adapter = (config) => {
      order.push('send')
      return Promise.resolve({ data: {}, status: 200, statusText: 'OK', headers: {}, config })
    }
    try {
      await request({ url: '/auth/login', method: 'POST' })
    } finally {
      if (original === undefined) {
        delete http.defaults.adapter
      } else {
        http.defaults.adapter = original
      }
    }

    expect(order).toEqual(['ensure', 'send'])
  })

  it('🔴 领设备身份那条请求自己不等 —— 等它就是等自己，全站永久挂起', async () => {
    const { seen, restore } = captureAdapter()
    configureHttp({
      onLoginRequired: () => {},
      // 真实实现在这里会去调 /device/register，从而回到本拦截器
      ensureDevice: () => Promise.reject(new Error('不该被调用')),
    })

    try {
      await request({ url: DEVICE_REGISTER_URL, method: 'POST', data: { deviceType: 'H5' } })
    } finally {
      restore()
    }

    expect(seen).toHaveLength(1)
  })

  it('🔴 不传 ensureDevice = 不管设备，而不是沿用上一次注入的', async () => {
    const { seen, restore } = captureAdapter()
    // beforeEach 刚注入过一个。「传了才覆盖」的写法下，这一行改不掉它，而且没有任何办法看出来
    configureHttp({ onLoginRequired: vi.fn() })

    try {
      await request({ url: '/auth/login', method: 'POST' })
    } finally {
      restore()
    }

    expect(seen).toHaveLength(1)
    expect(ensureDevice).not.toHaveBeenCalled()
  })
})
