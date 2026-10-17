import { computed, type ComputedRef } from 'vue'

import { ApiError } from '@/api/errors'
import { useCountdown } from '@/composables/useCountdown'

/**
 * 被限流（429）之后「还要等多久」—— 兑换、签到、抽奖、领取、充值这些按钮共用（网关方案 T14）。
 *
 * <h3>为什么每颗按钮都要它</h3>
 * 网关被罚时回 {@code 429 OPERATION_LIMITED}，带 {@code Retry-After} 与 {@code details.retryAfterSeconds}。
 * 按钮不置灰的话，用户会一直点、一直被拒 —— 而每一次都是一趟真实请求，还会把处罚往高一档推。
 * 所以和登录页同一个做法：<b>不弹窗</b>（这一档唯一的出路就是等），按钮按倒计时置灰，到 0 自己恢复。
 *
 * <h3>只认带秒数的 429</h3>
 * 拿不到秒数就不倒计时，<b>不兜默认值</b> —— 倒计时从一个编出来的数字开始走，
 * 走到 0 用户以为能点了，结果又是一次 429（{@link ApiError#retryAfterSeconds}）。
 * {@code 400 INVALID_ARGUMENT}（关键字拦截也回它）不做任何特殊处理：那是一次普通的失败。
 *
 * <pre>
 * const wait = useRetryAfter()
 * try { await sign() } catch (e) { wait.catchLimited(e); hint.value = messageOf(e) }
 * &lt;Button :disabled="wait.active.value"&gt;{{ wait.label('签到') }}&lt;/Button&gt;
 * </pre>
 */
export interface RetryAfterWait {
  /** 正在等。按钮的 disabled 用它 */
  active: ComputedRef<boolean>
  /** `00:59` / `1:02:03` */
  text: ComputedRef<string>
  /**
   * 这个错误是「带秒数的 429」的话，开始倒计时并返回 true；否则什么都不做、返回 false。
   * 不吞错误：调用方照常展示 message。
   */
  catchLimited: (error: unknown) => boolean
  /** 按钮文案：在等时是「00:59 后可再试」，否则是 {@code idle} */
  label: (idle: string) => string
  stop: () => void
}

export function useRetryAfter(): RetryAfterWait {
  const countdown = useCountdown()

  function catchLimited(error: unknown): boolean {
    if (!(error instanceof ApiError) || error.status !== 429) {
      return false
    }
    const seconds = error.retryAfterSeconds
    if (seconds === null) {
      return false
    }
    countdown.start(seconds)
    return true
  }

  const active = computed(() => countdown.active.value)

  return {
    active,
    text: countdown.text,
    catchLimited,
    label: (idle: string) => (active.value ? `${countdown.text.value} 后可再试` : idle),
    stop: countdown.stop,
  }
}
