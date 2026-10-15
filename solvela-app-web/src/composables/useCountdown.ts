import { computed, onScopeDispose, ref, type ComputedRef } from 'vue'

/**
 * 「还要等多久」的倒计时。被限流、密码冷却、发码冷却三处共用。
 *
 * <h3>为什么要有它，而不是各处写一个 setInterval</h3>
 * 这件事看起来三行就能写完，而它有四个坑，每一个都要在三处各踩一次：
 * <ul>
 *   <li>组件销毁时忘了清定时器 —— 之后它仍在改一个不存在的组件的 ref；</li>
 *   <li>重新开始时忘了先清上一个 —— 两个定时器同时在减，秒数跳着走；</li>
 *   <li>按墙上时钟算而不是减 1 —— 手机息屏时 `setInterval` 会被压制，
 *       回到前台时少走的那些秒不会自己补上，于是倒计时停在「还剩 23 秒」不动；</li>
 *   <li>到 0 之后没有回调 —— 调用方只好自己再起一个定时器去轮询那个 ref。</li>
 * </ul>
 *
 * <h3>🔴 按【到期时刻】算，不是每秒减一</h3>
 * 记下 `截止时间 = 现在 + 秒数`，每次 tick 重新算差值。这样息屏、切后台、
 * 浏览器节流定时器都不影响正确性 —— 回到前台的第一次 tick 就把时间对上了。
 */

export interface Countdown {
  /** 剩余秒数；没在倒计时时为 0 */
  secondsLeft: ComputedRef<number>
  /** 是否正在倒计时。按钮的 disabled 用它 */
  active: ComputedRef<boolean>
  /** `00:59` / `1:02:03`。够长时带上小时，24 小时那一档必须看得懂 */
  text: ComputedRef<string>
  /** 开始（或重新开始）。秒数 ≤ 0 时等同 {@link stop} */
  start: (seconds: number) => void
  stop: () => void
}

export function useCountdown(): Countdown {
  const secondsLeft = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null
  let deadline = 0

  function clear(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function tick(): void {
    const left = Math.ceil((deadline - Date.now()) / 1000)
    secondsLeft.value = left > 0 ? left : 0
    if (left <= 0) {
      clear()
    }
  }

  function start(seconds: number): void {
    clear()
    if (!Number.isFinite(seconds) || seconds <= 0) {
      secondsLeft.value = 0
      return
    }
    deadline = Date.now() + seconds * 1000
    tick()
    timer = setInterval(tick, 1000)
  }

  function stop(): void {
    clear()
    secondsLeft.value = 0
  }

  // 组件没了，定时器也得没 —— 留着它会在一个已经销毁的作用域里改 ref
  onScopeDispose(clear)

  const text = computed(() => {
    const total = secondsLeft.value
    const seconds = total % 60
    const minutes = Math.floor(total / 60) % 60
    const hours = Math.floor(total / 3600)
    const pad = (n: number): string => String(n).padStart(2, '0')
    // 24 小时那一档说「1440:00」没人看得懂，所以超过一小时就带上小时位
    return hours > 0
      ? `${hours}:${pad(minutes)}:${pad(seconds)}`
      : `${pad(minutes)}:${pad(seconds)}`
  })

  return {
    secondsLeft: computed(() => secondsLeft.value),
    active: computed(() => secondsLeft.value > 0),
    text,
    start,
    stop,
  }
}
