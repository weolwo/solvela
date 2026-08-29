import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'

import {
  fetchMe,
  login as loginApi,
  logout as logoutApi,
  type LoginPayload,
  type MemberProfile,
} from '@/api/auth'
import { ApiError } from '@/api/errors'
import { configureHttp } from '@/api/http'
import { clearToken, readToken, writeToken } from '@/utils/token-storage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken()?.token ?? null)
  const member = shallowRef<MemberProfile | null>(null)
  const restoring = ref(false)

  const isLoggedIn = computed(() => token.value !== null)

  function setSession(accessToken: string, expiresIn: number, profile: MemberProfile): void {
    writeToken(accessToken, expiresIn)
    token.value = accessToken
    member.value = profile
  }

  function clearSession(): void {
    clearToken()
    token.value = null
    member.value = null
  }

  async function login(payload: LoginPayload): Promise<void> {
    // 这里不吞异常：BAD_CREDENTIALS 必须原样抛给登录页去展示 message
    const result = await loginApi(payload)
    setSession(result.accessToken, result.expiresIn, result.member)
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
  })

  return { token, member, isLoggedIn, restoring, login, logout, restore, clearSession }
})
