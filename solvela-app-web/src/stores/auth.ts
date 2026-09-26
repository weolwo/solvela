import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'

import {
  fetchMe,
  login as loginApi,
  logout as logoutApi,
  register as registerApi,
  type LoginPayload,
  type MemberProfile,
  type RegisterPayload,
} from '@/api/auth'
import { ensureDevice } from '@/api/device'
import { ApiError } from '@/api/errors'
import { configureHttp } from '@/api/http'
import { finishStepUp, requestStepUp } from '@/composables/useStepUp'
import { clearToken, readToken, writeToken } from '@/utils/token-storage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken()?.token ?? null)
  const member = shallowRef<MemberProfile | null>(null)
  const restoring = ref(false)

  const isLoggedIn = computed(() => token.value !== null)

  function setSession(
    accessToken: string,
    expiresIn: number,
    profile: MemberProfile,
    remember: boolean,
  ): void {
    writeToken(accessToken, expiresIn, remember)
    token.value = accessToken
    member.value = profile
  }

  function clearSession(): void {
    // 会话没了，正在等的二次验证也就没有意义了 —— 按「放弃」结束，
    // 否则发起它的那个请求会一直挂着，按钮永远在转圈
    finishStepUp(false)
    clearToken()
    token.value = null
    member.value = null
  }

  /**
   * @param remember 「记住我」。true 存 localStorage（关掉浏览器还在），
   *                 false 存 sessionStorage（标签页一关就没）。见 token-storage
   */
  async function login(payload: LoginPayload, remember: boolean): Promise<void> {
    // 这里不吞异常：BAD_CREDENTIALS 必须原样抛给登录页去展示 message
    const result = await loginApi(payload)
    setSession(result.accessToken, result.expiresIn, result.member, remember)
  }

  /**
   * 注册成功即登录：后端直接把令牌一起返回了，所以这里与 login 走同一条 setSession。
   *
   * 同样不吞异常 —— CONFLICT（手机号已注册）必须原样抛给注册页，
   * 它要据此引导用户去登录，而不是只显示一行红字。
   */
  async function register(payload: RegisterPayload): Promise<void> {
    const result = await registerApi(payload)
    // 注册没有「记住我」勾选框：刚创建账号的人当然要留在登录态，
    // 再问一遍是多余的一步。所以固定持久化
    setSession(result.accessToken, result.expiresIn, result.member, true)
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // 服务端吊销失败不该把用户卡在登录态里，本地照样清干净
    } finally {
      clearSession()
    }
  }

  /**
   * 冷启动恢复会话：本地有令牌就跟后端确认一次。
   * 令牌已失效时后端返回 LOGIN_REQUIRED，拦截器会清会话，这里只需静默放行到登录页。
   */
  async function restore(): Promise<void> {
    if (token.value === null || member.value !== null || restoring.value) {
      return
    }
    restoring.value = true
    try {
      member.value = await fetchMe()
    } catch (error) {
      if (!(error instanceof ApiError && error.isLoginRequired)) {
        clearSession()
      }
    } finally {
      restoring.value = false
    }
  }

  configureHttp({
    getToken: () => token.value,
    onLoginRequired: clearSession,
    /*
     * 🔴 设备身份【不受登录态影响】：这里注入的是一个与 token 无关的函数。
     * 退出登录会清 token，但不碰设备令牌 —— 设备是设备，账号是账号。
     * 清掉的话，「换个号登录 = 换一台机器」，那正是刷子最想要的效果。
     */
    ensureDeviceToken: async () => (await ensureDevice('H5'))?.token ?? null,
    // 新设备上加地址 / 充话费时，服务端要求先验一次邮箱码。弹框由 StepUpDialog 负责
    onStepUpRequired: requestStepUp,
  })

  return { token, member, isLoggedIn, restoring, login, register, logout, restore, clearSession }
})
