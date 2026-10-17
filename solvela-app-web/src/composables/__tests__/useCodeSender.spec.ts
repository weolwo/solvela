import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/errors'
import { useCodeSender } from '../useCodeSender'

/**
 * 「获取验证码」那颗按钮的状态机。
 *
 * <h3>为什么这几条值得测</h3>
 * 它们都是**用户被卡住但服务端一切正常**的那类问题 —— 没有日志、没有报错，
 * 只有一个点不动的按钮，而报障的人说不清自己遇到了什么。
 *
 * <p>短信这一条尤其：服务端的 IP 日限比邮件紧得多（一条几分钱），
 * 冷却坏掉时用户连点的每一下都是一趟真实请求，烧的是真钱。
 */

/** composable 里用了 onScopeDispose，得跑在一个 scope 里 */
function inScope<T>(fn: () => T): { value: T; stop: () => void } {
  const scope = effectScope()
  const value = scope.run(fn) as T
  return { value, stop: () => scope.stop() }
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('倒计时', () => {
  it('🔴 只在【发出去之后】才开始 —— 失败时读秒，用户手上一条短信都没有', async () => {
    const { value: sender, stop } = inScope(() =>
      useCodeSender(() => Promise.reject(new ApiError('INTERNAL', '发送失败', null, 500)), {
        cooldownSeconds: 60,
      }),
    )

    await sender.send()

    expect(sender.canSend.value, '发失败了却进了冷却：这一分钟用户什么都做不了').toBe(true)
    expect(sender.error.value).toBe('发送失败')
    stop()
  })

  it('成功之后开始读秒，读完自己恢复', async () => {
    const { value: sender, stop } = inScope(() =>
      useCodeSender(() => Promise.resolve(), { cooldownSeconds: 3 }),
    )

    await sender.send()
    expect(sender.canSend.value).toBe(false)
    expect(sender.label.value).toBe('3s')

    await vi.advanceTimersByTimeAsync(3000)

    expect(sender.canSend.value).toBe(true)
    // 发过一次之后文案变成「重新获取」—— 用户要知道上一条是真的发出去了
    expect(sender.label.value).toBe('重新获取')
    stop()
  })

  it('🔴 被限频（429）时也要进冷却 —— 否则用户一直点一直被拒，每下都是真实请求', async () => {
    const { value: sender, stop } = inScope(() =>
      useCodeSender(
        () => Promise.reject(new ApiError('OPERATION_LIMITED', '请 42 秒后再试', null, 429)),
        { cooldownSeconds: 60 },
      ),
    )

    await sender.send()

    expect(sender.canSend.value).toBe(false)
    expect(sender.error.value).toBe('请 42 秒后再试')
    stop()
  })

  it('🔴 被限频（429）带了秒数时按服务端给的秒数倒计时 —— 网关的阶梯处罚能到 8 小时，按 60 秒读秒到 0 又是一次 429', async () => {
    const { value: sender, stop } = inScope(() =>
      useCodeSender(
        () =>
          Promise.reject(
            new ApiError('OPERATION_LIMITED', '操作过于频繁，请 2 小时后再试', null, 429, {
              retryAfterSeconds: 7200,
            }),
          ),
        { cooldownSeconds: 60 },
      ),
    )

    await sender.send()
    await vi.advanceTimersByTimeAsync(61_000)

    expect(sender.canSend.value).toBe(false)
    expect(sender.label.value).toBe('7139s')
    stop()
  })

  it('格式错（400）不进冷却 —— 那是用户能立刻改好的，罚他等 60 秒没道理', async () => {
    const { value: sender, stop } = inScope(() =>
      useCodeSender(
        () => Promise.reject(new ApiError('INVALID_ARGUMENT', '手机号格式不正确', null, 400)),
        { cooldownSeconds: 60 },
      ),
    )

    await sender.send()

    expect(sender.canSend.value).toBe(true)
    stop()
  })
})

describe('本地预检', () => {
  it('🔴 precheck 不通过时安静地什么都不做 —— 不发请求，也【不报「发送失败」】', async () => {
    const request = vi.fn(() => Promise.resolve())
    const { value: sender, stop } = inScope(() =>
      useCodeSender(request, { cooldownSeconds: 60, precheck: () => false }),
    )

    await sender.send()

    expect(request).not.toHaveBeenCalled()
    expect(
      sender.error.value,
      '「手机号还没填」不是一次失败的发送。报成「验证码发送失败，请稍后再试」' +
        '会让用户以为服务坏了，去反复重试，而不是去填手机号',
    ).toBeUndefined()
    // 什么都没发生过，按钮当然还能点
    expect(sender.canSend.value).toBe(true)
    stop()
  })
})

describe('并发与清理', () => {
  it('冷却期间再点不会真的发出去', async () => {
    const request = vi.fn(() => Promise.resolve())
    const { value: sender, stop } = inScope(() => useCodeSender(request, { cooldownSeconds: 60 }))

    await sender.send()
    await sender.send()
    await sender.send()

    expect(request).toHaveBeenCalledTimes(1)
    stop()
  })

  it('reset 之后可以立刻重发 —— 换了手机号就是换了一件事', async () => {
    const { value: sender, stop } = inScope(() =>
      useCodeSender(() => Promise.resolve(), { cooldownSeconds: 60 }),
    )

    await sender.send()
    expect(sender.canSend.value).toBe(false)

    sender.reset()

    expect(sender.canSend.value).toBe(true)
    expect(sender.error.value).toBeUndefined()
    expect(sender.label.value).toBe('获取验证码')
    stop()
  })

  it('🔴 scope 销毁后定时器要停 —— 否则它会在一个已经不存在的页面上改状态', async () => {
    const { value: sender, stop } = inScope(() =>
      useCodeSender(() => Promise.resolve(), { cooldownSeconds: 60 }),
    )
    await sender.send()

    stop()
    await vi.advanceTimersByTimeAsync(5000)

    // 还停在 60：定时器真的停了。没停的话这里会是 55
    expect(sender.label.value).toBe('60s')
  })
})
