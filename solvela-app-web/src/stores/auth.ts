import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'

import {
  fetchMe,
  verifyLoginChallenge,
  login as loginApi,
  logout as logoutApi,
  register as registerApi,
  type LoginPayload,
  type MemberProfile,
  type RegisterPayload,
} from '@/api/auth'
import { ensureDevice } from '@/api/device'
import { configureHttp } from '@/api/http'
import { showAccountNotice } from '@/composables/useAccountNotice'
import { requestCaptcha } from '@/composables/useCaptcha'
import { finishStepUp, requestStepUp } from '@/composables/useStepUp'
import { clearSessionHint, hasSessionHint, setSessionHint } from '@/utils/session-hint'

/**
 * 登录态。
 *
 * <h3>🔴 这里没有令牌</h3>
 * 会话令牌在服务端下发的 HttpOnly cookie 里，页面脚本读不到 —— 这是刻意的：
 * 以前它存在 localStorage，混进页面的任何脚本都能把它读走、带回自己的机器用满 30 天。
 *
 * 代价是「现在登着吗」没法同步回答，要问服务端（`/auth/me`）。
 * 所以 {@link isLoggedIn} 看的是「有没有会员资料」，由 {@link restore} 在启动时填上；
 * 路由守卫先 await 它再判断。为了不让每个匿名访客都白吃一个 401，
 * 只有本地有「上次登着」的提示时才去问（见 utils/session-hint）。
 */
export const useAuthStore = defineStore('auth', () => {
  const member = shallowRef<MemberProfile | null>(null)
  const restoring = ref(false)

  const isLoggedIn = computed(() => member.value !== null)

  function setSession(profile: MemberProfile): void {
    member.value = profile
    setSessionHint()
  }

  /**
   * 清掉本地的登录态。**清不掉 cookie** —— 那是 HttpOnly 的，只有服务端能清
   * （退出登录接口、或服务端发现令牌失效时顺手清）。
   */
  function clearSession(): void {
    // 会话没了，正在等的二次验证也就没有意义了 —— 按「放弃」结束，
    // 否则发起它的那个请求会一直挂着，按钮永远在转圈
    finishStepUp(false)
    clearSessionHint()
    member.value = null
  }

  /**
   * @param remember 「记住我」。true = 持久 cookie（关掉浏览器还在），
   *                 false = 会话 cookie（关掉浏览器就没了）。由服务端按这个值下发
   */
  async function login(payload: LoginPayload, remember: boolean): Promise<void> {
    // 这里不吞异常：BAD_CREDENTIALS 必须原样抛给登录页去展示 message
    const result = await loginApi(payload, remember)
    setSession(result.member)
  }

  /**
   * 登录二次验证通过即登录成功（这台设备处在观察档时）。不吞异常：
   * BAD_CREDENTIALS（码错）要挂在验证码栏上，CHALLENGE_EXPIRED 要让登录页回到输密码那一步。
   */
  async function completeLoginChallenge(
    ticket: string,
    code: string,
    remember: boolean,
  ): Promise<void> {
    const result = await verifyLoginChallenge(ticket, code, remember)
    setSession(result.member)
  }

  /**
   * 注册成功即登录：服务端注册时直接签了会话 cookie。
   *
   * 同样不吞异常 —— CONFLICT（手机号已注册）必须原样抛给注册页，
   * 它要据此引导用户去登录，而不是只显示一行红字。
   */
  async function register(payload: RegisterPayload): Promise<void> {
    const result = await registerApi(payload)
    setSession(result.member)
  }

  async function logout(): Promise<void> {
    try {
      // 服务端吊销令牌并清 cookie。设备 cookie 不动 —— 退出账号不等于换了一台机器
      await logoutApi()
    } catch {
      // 服务端吊销失败不该把用户卡在登录态里，本地照样清干净
    } finally {
      clearSession()
    }
  }

  let restoreTask: Promise<void> | null = null

  async function doRestore(): Promise<void> {
    if (!hasSessionHint()) {
      return
    }
    restoring.value = true
    try {
      member.value = await fetchMe()
    } catch {
      // LOGIN_REQUIRED：拦截器已经 clearSession（连提示一起擦掉），下次不会再问。
      // 网络错误：提示留着，下一次导航再试 —— 不因为一次抖动就当成已退出
    } finally {
      restoring.value = false
    }
  }

  /**
   * 冷启动恢复会话。路由守卫每次导航都会调它，所以必须便宜：
   * 已登录直接返回；并发调用共用同一次请求；没有「上次登着」的提示时一个请求都不发。
   */
  function restore(): Promise<void> {
    if (member.value !== null) {
      return Promise.resolve()
    }
    if (restoreTask === null) {
      restoreTask = doRestore().finally(() => {
        restoreTask = null
      })
    }
    return restoreTask
  }

  configureHttp({
    onLoginRequired: clearSession,
    /*
     * 🔴 设备身份【不受登录态影响】：退出登录只清会话，不碰设备 cookie ——
     * 设备是设备，账号是账号。清掉的话，「换个号登录 = 换一台机器」，那正是刷子最想要的效果。
     */
    ensureDevice: async () => {
      await ensureDevice('H5')
    },
    // 新设备上加地址 / 充话费时，服务端要求先验一次邮箱码。弹框由 StepUpDialog 负责
    onStepUpRequired: requestStepUp,
    // 发码、密码登录之前要过滑块。弹窗由 CaptchaDialog 负责，它不依赖登录态
    onCaptchaRequired: requestCaptcha,
    /*
     * 账号状态挡住了这次操作：资产出口冻结、没绑联系方式、新绑的还在冷静期。
     * 弹窗由 AccountNoticeDialog 负责。
     * 🔴 一律不重试原请求 —— 这三档都不是「还差一步就能继续」。
     */
    onAccountNotice: (error) => {
      if (error.code === 'CONTACT_BIND_REQUIRED') {
        showAccountNotice({
          code: 'CONTACT_BIND_REQUIRED',
          message: error.message,
          need: error.contactNeed,
        })
        return
      }
      if (error.code === 'CONTACT_COOLING') {
        showAccountNotice({
          code: 'CONTACT_COOLING',
          message: error.message,
          availableAt: error.contactAvailableAt,
        })
        return
      }
      showAccountNotice({
        code: 'ASSET_OUT_FROZEN',
        message: error.message,
        unlock: error.assetOutUnlock,
      })
    },
  })

  return {
    member,
    isLoggedIn,
    restoring,
    login,
    completeLoginChallenge,
    register,
    logout,
    restore,
    clearSession,
  }
})
