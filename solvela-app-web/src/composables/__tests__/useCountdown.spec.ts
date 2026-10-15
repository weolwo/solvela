import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useCountdown } from '../useCountdown'

/**
 * 倒计时。
 *
 * <h3>钉住的是那几个容易漏的地方</h3>
 * <ul>
 *   <li>🔴 **按到期时刻算，不是每秒减一** —— 手机息屏时 setInterval 会被压制，
 *       减一的写法回到前台会停在「还剩 23 秒」不动；</li>
 *   <li>重新开始时先清掉上一个，否则两个定时器同时在减，秒数跳着走；</li>
 *   <li>作用域销毁时定时器必须停 —— 留着它会去改一个已经不存在的 ref。</li>
 * </ul>
 */

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

/** 在一个作用域里跑，才能验 onScopeDispose */
function inScope<T>(fn: () => T): { value: T; dispose: () => void } {
  const scope = effectScope()
  const value = scope.run(fn)!
  return { value, dispose: () => scope.stop() }
}

describe('useCountdown', () => {
  it('start 之后开始走，到 0 自动停', () => {
    const { value: c } = inScope(() => useCountdown())

    c.start(3)
    expect(c.secondsLeft.value).toBe(3)
    expect(c.active.value).toBe(true)

    vi.advanceTimersByTime(3000)
    expect(c.secondsLeft.value).toBe(0)
    expect(c.active.value).toBe(false)
    expect(vi.getTimerCount(), '到 0 还留着定时器就是每秒空转').toBe(0)
  })

  it('🔴 息屏导致的 tick 缺失会被补上：按到期时刻算，不是每秒减一', () => {
    const { value: c } = inScope(() => useCountdown())

    c.start(120)
    // 只走一次 tick，但墙上时钟已经过去 100 秒 —— 模拟被压制的定时器
    vi.setSystemTime(Date.now() + 100_000)
    vi.advanceTimersByTime(1000)

    // 100 秒（息屏）+ 1 秒（这一次 tick）= 101，所以剩 19
    expect(c.secondsLeft.value, '减一的写法这里会是 119，然后一直慢 100 秒').toBe(19)
  })

  it('重新 start 会接管，不会留下两个定时器一起减', () => {
    const { value: c } = inScope(() => useCountdown())

    c.start(60)
    c.start(10)

    expect(vi.getTimerCount()).toBe(1)
    expect(c.secondsLeft.value).toBe(10)
  })

  it('stop 立刻清零，按钮可以马上放开', () => {
    const { value: c } = inScope(() => useCountdown())

    c.start(60)
    c.stop()

    expect(c.secondsLeft.value).toBe(0)
    expect(c.active.value).toBe(false)
  })

  it('秒数非法（0、负数、NaN）时不启动 —— 服务端没给就别编一个出来', () => {
    const { value: c } = inScope(() => useCountdown())

    c.start(0)
    expect(c.active.value).toBe(false)
    c.start(-5)
    expect(c.active.value).toBe(false)
    c.start(Number.NaN)
    expect(c.active.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('文案：一小时以内 mm:ss，超过一小时带上小时位', () => {
    const { value: c } = inScope(() => useCountdown())

    c.start(59)
    expect(c.text.value).toBe('00:59')
    c.start(300)
    expect(c.text.value).toBe('05:00')
    c.start(24 * 3600)
    expect(c.text.value, '24 小时那一档说「1440:00」没人看得懂').toBe('24:00:00')
  })

  it('🔴 作用域销毁后定时器也没了', () => {
    const { value: c, dispose } = inScope(() => useCountdown())

    c.start(60)
    expect(vi.getTimerCount()).toBe(1)

    dispose()
    expect(vi.getTimerCount()).toBe(0)
  })
})
