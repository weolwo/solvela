import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'

import { toApiError } from './errors'

/**
 * HTTP 客户端。
 *
 * 三条约定，改之前先看 errors.ts 的说明：
 *   1. 成功时 `response.data` **就是业务数据**，没有信封要剥。
 *   2. 失败时抛 {@link ApiError}，业务代码不接触 AxiosError。
 *   3. 只有 LOGIN_REQUIRED 触发「清会话 + 跳登录」。
 *      BAD_CREDENTIALS 同样是 401，但它是「这次密码输错了」，必须原样抛给登录页，
 *      否则用户会被弹回登录页而看不到「手机号或密码错误」这句提示。
 */

type TokenProvider = () => string | null
type UnauthorizedHandler = () => void

let tokenProvider: TokenProvider = () => null
let unauthorizedHandler: UnauthorizedHandler = () => {}

/** 由 stores/auth 在初始化时注入，避免 http 反向依赖 store 造成循环引用 */
export function configureHttp(options: {
  getToken: TokenProvider
  onLoginRequired: UnauthorizedHandler
}): void {
  tokenProvider = options.getToken
  unauthorizedHandler = options.onLoginRequired
}

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = tokenProvider()
  if (token !== null && token !== '') {
    // header 名与 scheme 对齐 solvela-app 的 solvela.app.auth 配置
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error instanceof Error ? error : new Error('请求失败，请稍后再试'))
    }

    const status = error.response?.status ?? null
    const apiError = toApiError(
      status,
      error.response?.data,
      status === null ? '网络连接失败，请检查网络后重试' : '服务开小差了，请稍后再试',
    )

    if (apiError.isLoginRequired) {
      unauthorizedHandler()
    }

    return Promise.reject(apiError)
  },
)

/** 发请求并直接拿到业务数据（没有信封这一层） */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<T>(config)
  return response.data
}

/** 用于 204 之类没有响应体的接口 */
export async function requestVoid(config: AxiosRequestConfig): Promise<void> {
  await http.request(config)
}

export default http
