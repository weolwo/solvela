import { computed, ref, type ComputedRef, type Ref } from 'vue'

import { ApiError } from '@/api/errors'
import { useCountdown } from '@/composables/useCountdown'

/**
 * 「获取验证码」那颗按钮的全部状态：倒计时、发送中、失败提示。
 *
 * <h3>为什么是 composable 而不是组件</h3>
 * 短信和邮箱两条通道、四个场景（注册 / 登录 / 绑定 / 重置）都要这颗按钮，
 * 但**每一处的输入框长得不一样**：注册页它贴在手机号框右边，绑定页在邮箱框右边，
 * 重置页还要先填邮箱。做成组件就得为了适配这些差异开一堆 prop，
 * 而真正共用的只有下面这段状态机。
 *
 * <h3>🔴 倒计时只在【发出去之后】开始</h3>
 * 点下去就开始倒计时的写法很常见，也很坑：请求失败时用户看着 60 秒读秒，
 * 而他手上一条短信都没有 —— 那一分钟他什么都做不了，只能反复退出重进。
 *
 * <h3>🔴 被限频（429）时【也要】倒计时，而且按服务端给的秒数</h3>
 * 那是服务端在说「你现在发不出去」。不倒计时的话按钮立刻又可点，
 * 用户会一直点一直被拒，而每一次都是一趟真实的请求（还会把网关的阶梯处罚往高一档推）。
 * 秒数取 {@code retryAfterSeconds}：网关的阶梯处罚能到 8 小时，按固定 60 秒读秒的话，
 * 走到 0 用户以为能点了，结果又是一次 429。服务端没给秒数时才退回发码冷却的那 60 秒。
 *
 * <p>倒计时用 {@link useCountdown}（按到期时刻算）：手机息屏时 setInterval 会被压制，
 * 每秒减一的写法回到前台时会停在「还剩 23 秒」不动。
 */

/** 与后端 resend-cooldown 对齐。两边不一致时，宁可这边长一点 */
const DEFAULT_COOLDOWN_SECONDS = 60

export interface CodeSender {
  /** 按钮文案：获取验证码 / 发送中 / 59s 后重发 */
  label: ComputedRef<string>
  /** 按钮是否可点 */
  canSend: ComputedRef<boolean>
  /** 发送失败时的提示，挂在验证码框上 */
  error: Ref<string | undefined>
  /** 已经成功发出去过至少一次 —— 用来把提示语从「获取」改成「已发送到 xxx」 */
  sent: Ref<boolean>
  send: () => Promise<void>
  /** 换了手机号/邮箱时调：之前那个码是发给别的号的，状态得跟着清 */
  reset: () => void
}

export interface CodeSenderOptions {
  cooldownSeconds?: number
  /**
   * 发之前的本地检查。返回 false 就**安静地什么都不做**。
   *
   * 🔴 它存在的理由：本地校验失败不是「发送失败」。
   * 早先这一步是靠在 request 里 throw 实现的，于是「手机号还没填」会同时点亮
   * 两条提示 —— 手机号框上一条「请先输入手机号」，验证码框上再来一条
   * 「验证码发送失败，请稍后再试」。后面那句是**假的**：什么都没发生过，
   * 而它会让用户以为服务出了问题，去反复重试而不是去填手机号。
   *
   * <p>所以提示由调用方自己挂在**该挂的那一栏**上，这里只负责不发。
   */
  precheck?: () => boolean
}

/**
 * @param request 真正发请求的那个函数。**校验不放在它里面**，见 {@link CodeSenderOptions.precheck}
 */
export function useCodeSender(
  request: () => Promise<void>,
  options: CodeSenderOptions = {},
): CodeSender {
  const cooldownSeconds = options.cooldownSeconds ?? DEFAULT_COOLDOWN_SECONDS
  const sending = ref(false)
  const error = ref<string | undefined>(undefined)
  const sent = ref(false)
  // 页面被销毁时它自己停（onScopeDispose 在 useCountdown 里）
  const countdown = useCountdown()
  const secondsLeft = countdown.secondsLeft

  const canSend = computed(() => !sending.value && secondsLeft.value <= 0)

  const label = computed(() => {
    if (sending.value) {
      return '发送中'
    }
    if (secondsLeft.value > 0) {
      return `${secondsLeft.value}s`
    }
    return sent.value ? '重新获取' : '获取验证码'
  })

  async function send(): Promise<void> {
    if (!canSend.value) {
      return
    }
    error.value = undefined
    if (options.precheck !== undefined && !options.precheck()) {
      return
    }
    sending.value = true
    try {
      await request()
      sent.value = true
      countdown.start(cooldownSeconds)
    } catch (e) {
      if (e instanceof ApiError) {
        error.value = e.message
        /*
         * 🔴 被限频时也进冷却。服务端已经说了「你现在发不出去」，
         * 让按钮立刻又可点，用户就会一直点一直被拒，而每一次都是真实请求。
         *
         * 格式错（INVALID_ARGUMENT）不进冷却：那是用户能立刻改好的东西，
         * 罚他等 60 秒没有道理。
         */
        if (e.code === 'OPERATION_LIMITED') {
          countdown.start(e.retryAfterSeconds ?? cooldownSeconds)
        }
      } else {
        error.value = '验证码发送失败，请稍后再试'
      }
    } finally {
      sending.value = false
    }
  }

  function reset(): void {
    countdown.stop()
    sending.value = false
    sent.value = false
    error.value = undefined
  }

  return { label, canSend, error, sent, send, reset }
}
