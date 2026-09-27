import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import http, { configureHttp, request } from '../http'

/**
 * 滑块验证码：CAPTCHA_REQUIRED → 弹滑块 → 拿到通行票 → 放进 X-Captcha-Token 头原样重试。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 通行票真的放进了请求头 —— 放错地方的表现是「拖对了也永远发不出码」；</li>
 *   <li>🔴 重试后仍被要求 → 不再弹，原样抛（防死循环）；</li>
 *   <li>用户取消 → 原样抛 CAPTCHA_REQUIRED，不重试。</li>
 * </ul>
 */

const REQUIRED = { code: 'CAPTCHA_REQUIRED', message: '请先完成滑块验证', traceId: 't1' }

type Reply = { status: number; data: unknown }

function scriptedAdapter(replies: Reply[]): {
  seen: InternalAxiosRequestConfig[]
  restore: () => void
} {
  const seen: InternalAxiosRequestConfig[] = []
  const original = http.defaults.adapter
  let i = 0
  http.defaults.adapter = (config) => {
    seen.push(config)
    const reply = replies[Math.min(i, replies.length - 1)]!
    i += 1
    const response: AxiosResponse = {
      data: reply.data,
      status: reply.status,
      statusText: '',
      headers: {},
      config,
    }
    return reply.status >= 400
      ? Promise.reject(new AxiosError('fail', 'ERR_BAD_REQUEST', config, null, response))
      : Promise.resolve(response)
  }
  return {
    seen,
    restore: () => {
      if (original === undefined) {
        delete http.defaults.adapter
      } else {
        http.defaults.adapter = original
      }
    },
  }
}

function header(config: InternalAxiosRequestConfig | undefined, name: string): unknown {
  return (
    config?.headers?.get?.(name) ?? (config?.headers as Record<string, unknown> | undefined)?.[name]
  )
}

let captcha: ReturnType<typeof vi.fn<() => Promise<string | null>>>

beforeEach(() => {
  captcha = vi.fn<() => Promise<string | null>>()
  configureHttp({ onLoginRequired: () => {}, onCaptchaRequired: captcha })
})

afterEach(() => {
  configureHttp({ onLoginRequired: () => {} })
})

describe('滑块验证码', () => {
  it('🔴 拖对 → 通行票放进 X-Captcha-Token 头，原请求重试成功', async () => {
    captcha.mockResolvedValue('pass-1')
    const { seen, restore } = scriptedAdapter([
      { status: 403, data: REQUIRED },
      { status: 204, data: '' },
    ])
    try {
      await request({
        url: '/auth/email/code',
        method: 'POST',
        data: { scene: 'LOGIN', email: 'a@b.c' },
      })
    } finally {
      restore()
    }
    expect(captcha).toHaveBeenCalledTimes(1)
    expect(seen).toHaveLength(2)
    expect(header(seen[0], 'X-Captcha-Token')).toBeUndefined()
    expect(header(seen[1], 'X-Captcha-Token')).toBe('pass-1')
    expect(seen[1]?.url).toBe('/auth/email/code')
  })

  it('用户取消 → 原样抛 CAPTCHA_REQUIRED，不重试', async () => {
    captcha.mockResolvedValue(null)
    const { seen, restore } = scriptedAdapter([{ status: 403, data: REQUIRED }])
    try {
      await expect(request({ url: '/auth/login', method: 'POST' })).rejects.toMatchObject({
        code: 'CAPTCHA_REQUIRED',
      })
    } finally {
      restore()
    }
    expect(seen).toHaveLength(1)
  })

  it('🔴 带着票重试后仍被要求 → 不再弹第二次（防死循环）', async () => {
    captcha.mockResolvedValue('pass-1')
    const { seen, restore } = scriptedAdapter([{ status: 403, data: REQUIRED }])
    try {
      await expect(request({ url: '/auth/login', method: 'POST' })).rejects.toMatchObject({
        code: 'CAPTCHA_REQUIRED',
      })
    } finally {
      restore()
    }
    expect(captcha).toHaveBeenCalledTimes(1)
    expect(seen).toHaveLength(2)
  })
})
