import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/errors'

import { useRetryAfter } from '../useRetryAfter'

/**
 * 被限流后按钮按倒计时置灰（网关方案 T14）。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>只认「带秒数的 429」：拿不到秒数不倒计时 —— 编一个数字出来，走到 0 又是一次 429；</li>
 *   <li>{@code 400 INVALID_ARGUMENT}（关键字拦截也回它）不做任何特殊处理；</li>
 *   <li>到 0 自己恢复，按钮文案回到原样。</li>
 * </ul>
 */

function limited(seconds?: number): ApiError {
  return new ApiError(
    'OPERATION_LIMITED',
    '操作过于频繁，请稍后再试',
    null,
    429,
    seconds === undefined ? null : { retryAfterSeconds: seconds },
  )
}

describe('useRetryAfter', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function inScope() {
    const scope = effectScope()
    const wait = scope.run(() => useRetryAfter())!
    return { wait, stop: () => scope.stop() }
  }

  it('🔴 带秒数的 429 → 开始倒计时，按钮文案变成「mm:ss 后可再试」，到 0 自己恢复', async () => {
    const { wait, stop } = inScope()

    expect(wait.catchLimited(limited(90))).toBe(true)
    expect(wait.active.value).toBe(true)
    expect(wait.label('签到')).toBe('01:30 后可再试')

    await vi.advanceTimersByTimeAsync(90_000)
    expect(wait.active.value).toBe(false)
    expect(wait.label('签到')).toBe('签到')
    stop()
  })

  it('🔴 429 但没带秒数 → 不倒计时（不兜默认值）', () => {
    const { wait, stop } = inScope()

    expect(wait.catchLimited(limited())).toBe(false)
    expect(wait.active.value).toBe(false)
    stop()
  })

  it('400（关键字拦截也是它）、其它错误、非 ApiError 都不倒计时', () => {
    const { wait, stop } = inScope()

    expect(wait.catchLimited(new ApiError('INVALID_ARGUMENT', '请求参数有误', null, 400))).toBe(false)
    expect(wait.catchLimited(new ApiError('CONFLICT', '已经领过了', null, 409, { retryAfterSeconds: 60 }))).toBe(false)
    expect(wait.catchLimited(new Error('boom'))).toBe(false)
    expect(wait.active.value).toBe(false)
    stop()
  })

  it('几小时的处罚带上小时位 —— 「480:00」没人看得懂', () => {
    const { wait, stop } = inScope()

    wait.catchLimited(limited(8 * 3600))
    expect(wait.label('兑换')).toBe('8:00:00 后可再试')
    stop()
  })
})
