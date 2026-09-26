import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../errors'
import http, { configureHttp, request } from '../http'

/**
 * 二次验证：STEP_UP_REQUIRED → 弹框 → 通过后自动重试原请求。
 *
 * <h3>最要紧的两条</h3>
 * <ul>
 *   <li>🔴 重试之后仍然要求验证时<b>不再弹第二次</b> —— 否则服务端一旦没记住设备，
 *       用户会陷进「输码、再弹、再输码」的死循环；</li>
 *   <li>🔴 并发撞上时<b>只弹一次</b> —— 否则一次保存弹两个框、发两封信。</li>
 * </ul>
 */

const STEP_UP_BODY = {
  code: 'STEP_UP_REQUIRED',
  message: '为了你的账号安全，请先完成邮箱验证',
  traceId: 't1',
}

type Reply = { status: number; data: unknown }

/** 按顺序回放给定的响应；记下每次真正发出去的 config */
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
    // 自定义 adapter 要自己判状态：内置 adapter 里的 settle 这一步不会替它做
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

let stepUp: ReturnType<typeof vi.fn<() => Promise<boolean>>>

beforeEach(() => {
  stepUp = vi.fn<() => Promise<boolean>>()
  configureHttp({ onLoginRequired: () => {}, onStepUpRequired: stepUp })
})

afterEach(() => {
  configureHttp({ onLoginRequired: () => {} })
})

describe('二次验证', () => {
  it('验证通过 → 原请求自动重试，调用方拿到的是重试后的结果', async () => {
    stepUp.mockResolvedValue(true)
    const { seen, restore } = scriptedAdapter([
      { status: 403, data: STEP_UP_BODY },
      { status: 200, data: { id: 7 } },
    ])
    try {
      const result = await request<{ id: number }>({
        url: '/address',
        method: 'POST',
        data: { a: 1 },
      })
      expect(result).toEqual({ id: 7 })
    } finally {
      restore()
    }
    expect(stepUp).toHaveBeenCalledTimes(1)
    expect(seen).toHaveLength(2)
    // 重试的是【同一个】请求：路径、方法、请求体都不变
    expect(seen[1]?.url).toBe('/address')
    expect(seen[1]?.method).toBe('post')
    expect(seen[1]?.data).toBe(seen[0]?.data)
  })

  it('用户放弃 → 原样抛 STEP_UP_REQUIRED，不重试', async () => {
    stepUp.mockResolvedValue(false)
    const { seen, restore } = scriptedAdapter([{ status: 403, data: STEP_UP_BODY }])
    try {
      await expect(request({ url: '/address', method: 'POST' })).rejects.toMatchObject({
        code: 'STEP_UP_REQUIRED',
      })
    } finally {
      restore()
    }
    expect(seen).toHaveLength(1)
  })

  it('🔴 重试后仍要求验证 → 不再弹第二次，直接抛（防死循环）', async () => {
    stepUp.mockResolvedValue(true)
    const { seen, restore } = scriptedAdapter([{ status: 403, data: STEP_UP_BODY }])
    try {
      const error = await request({ url: '/address', method: 'POST' }).catch((e: unknown) => e)
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).isStepUpRequired).toBe(true)
    } finally {
      restore()
    }
    expect(stepUp).toHaveBeenCalledTimes(1)
    expect(seen).toHaveLength(2)
  })

  it('🔴 两个请求同时撞上 → 只弹一次框，通过后各自重试', async () => {
    let release: (v: boolean) => void = () => {}
    stepUp.mockImplementation(
      () =>
        new Promise<boolean>((resolve) => {
          release = resolve
        }),
    )
    const { seen, restore } = scriptedAdapter([
      { status: 403, data: STEP_UP_BODY },
      { status: 403, data: STEP_UP_BODY },
      { status: 200, data: 'ok' },
    ])
    try {
      const a = request({ url: '/address', method: 'POST' })
      const b = request({ url: '/recharge/order', method: 'POST' })
      // 等两个请求都撞上 403、都挂在同一个验证上
      await vi.waitFor(() => expect(seen).toHaveLength(2))
      release(true)
      await Promise.all([a, b])
    } finally {
      restore()
    }
    expect(stepUp).toHaveBeenCalledTimes(1)
    expect(seen).toHaveLength(4)
  })

  it('没注入处理函数（默认）→ 原样抛给调用方', async () => {
    configureHttp({ onLoginRequired: () => {} })
    const { restore } = scriptedAdapter([{ status: 403, data: STEP_UP_BODY }])
    try {
      await expect(request({ url: '/address', method: 'POST' })).rejects.toMatchObject({
        code: 'STEP_UP_REQUIRED',
      })
    } finally {
      restore()
    }
  })
})
