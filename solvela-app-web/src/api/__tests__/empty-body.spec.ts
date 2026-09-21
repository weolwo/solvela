import type { AxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import http, { request } from '../http'

/**
 * 空响应体必须归一成 null。
 *
 * <h3>🔴 这条测试对应一次白屏</h3>
 * 2026-09-21：彩票活动页点进去只有两个灰色骨架块，一直不动。
 *
 * 根因是<b>类型和运行时对不上</b>：后端返回 {@code null} 时，HTTP 上是
 * 200 + {@code Content-Length: 0} + 没有 {@code Content-Type}，
 * axios 没有可解析的东西，于是 {@code response.data} 是<b>空字符串</b>。
 *
 * <p>而接口签名写的是 {@code Promise<LotteryBoard | null>}，TypeScript 也认，
 * 页面照着写了 {@code board === null} 的判空 —— 那个判据永远不成立。
 * 于是走进「有数据」分支，对着一个空字符串读属性，在 render 里抛 TypeError；
 * Vue 在 patch 中途抛出，DOM 根本没走出加载态，所以骨架屏永远停在那儿。
 *
 * <h3>为什么在这一层测，而不是在页面里</h3>
 * 页面级 spec 都把 api 模块整个 mock 掉了，桩数据是手写的 {@code null} ——
 * <b>正好绕过出问题的那一层</b>。和 normalize.spec.ts 是同一个道理。
 *
 * <p>⚠️ 它也不会在联调里被发现：只有后端真的返回 null 那一次才触发，
 * 而那通常是「这个活动还没配玩法」这类正常的运营空态，
 * 平时点不到。
 */

/** 把 adapter 换掉：请求不出网，由我们决定服务端「返回」了什么 */
function replyWith(data: unknown, headers: Record<string, string> = {}): () => void {
  const original = http.defaults.adapter
  http.defaults.adapter = (config: AxiosRequestConfig) =>
    Promise.resolve({ data, status: 200, statusText: 'OK', headers, config })
  return () => {
    http.defaults.adapter = original
  }
}

describe('request 的空响应体', () => {
  it('🔴 200 + 空 body（后端返回 null）要拿到 null，不是空字符串', async () => {
    const restore = replyWith('')
    try {
      // 这正是 GET /lottery/board/{code} 在活动没挂上线玩法时的真实响应
      const result = await request<{ lotteryCode: string } | null>({ url: '/lottery/board/X' })

      expect(result).toBeNull()
      // 写成 !== null 的判空要能挡住它 —— 挡不住就会去读它的属性
      expect(result === null).toBe(true)
    } finally {
      restore()
    }
  })

  it('正常对象原样返回，别被归一顺手改掉', async () => {
    const restore = replyWith({ lotteryCode: 'ABC' })
    try {
      expect(await request<{ lotteryCode: string }>({ url: '/x' })).toEqual({ lotteryCode: 'ABC' })
    } finally {
      restore()
    }
  })

  it('空数组和空对象不是「没有响应体」，不许变成 null', async () => {
    // 归一的判据必须是严格等于空字符串。写成 falsy 判断的话，
    // 「查到 0 条」会变成「查不到」—— 而页面对这两件事说的话完全不同
    const restoreArray = replyWith([])
    try {
      expect(await request<unknown[]>({ url: '/x' })).toEqual([])
    } finally {
      restoreArray()
    }

    const restoreObject = replyWith({})
    try {
      expect(await request<object>({ url: '/x' })).toEqual({})
    } finally {
      restoreObject()
    }
  })

  it('数字 0 和 false 也不许被当成空', async () => {
    const restoreZero = replyWith(0)
    try {
      expect(await request<number>({ url: '/x' })).toBe(0)
    } finally {
      restoreZero()
    }

    const restoreFalse = replyWith(false)
    try {
      expect(await request<boolean>({ url: '/x' })).toBe(false)
    } finally {
      restoreFalse()
    }
  })
})
