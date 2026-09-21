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
type DeviceTokenProvider = () => Promise<string | null>

let tokenProvider: TokenProvider = () => null
let unauthorizedHandler: UnauthorizedHandler = () => {}
/** 默认不带设备头：注入之前（比如单测里）行为退化成「没有设备身份」，而不是报错 */
let deviceTokenProvider: DeviceTokenProvider = () => Promise.resolve(null)

/**
 * 领设备身份的那条路由。
 *
 * 🔴 常量放在 http.ts 而不是 device.ts，是为了**避免循环引用**：
 * device.ts 要用 http 发请求，http 的拦截器又要认出这条路由并跳过它。
 * 方向做成 device → http 这一条，就没有环。
 */
export const DEVICE_REGISTER_URL = '/device/register'

/**
 * 设备令牌放在这个头里。对齐网关的 `solvela.app.device.header`
 * （`DeviceAuthProperties.DEFAULT_HEADER`）。
 *
 * 🔴 **不是 `X-Device-Id`**。那是另一个头，方向也不同：
 * <ul>
 *   <li>`X-Device-Token`（本项）客户端 → 网关，装的是**令牌**，网关要验签；</li>
 *   <li>`X-Device-Id` 网关 → 内部服务，装的是**验签通过的设备号**。
 *       令牌绝不原样透传下去 —— 那等于把凭证散给所有内部服务
 *       （见 DownstreamClientConfig 的注释）。</li>
 * </ul>
 *
 * 写错的代价是**静默的**：网关读不到这个头，就当作「没有设备身份」放行，
 * 请求全部成功，只是 device_id 恒为 NULL、覆盖率恒为 0% —— 而那正是
 * 整套设备方案唯一的产出。2026-09-10 第一版客户端就是这么错的。
 */
const DEVICE_HEADER = 'X-Device-Token'

/** 由 stores/auth 在初始化时注入，避免 http 反向依赖 store 造成循环引用 */
export function configureHttp(options: {
  getToken: TokenProvider
  onLoginRequired: UnauthorizedHandler
  /** 可选：拿设备令牌。**不传就是「不带设备头」**，不是「沿用上一次」 */
  ensureDeviceToken?: DeviceTokenProvider
}): void {
  tokenProvider = options.getToken
  unauthorizedHandler = options.onLoginRequired
  /*
   * 🔴 无条件赋值，不写成「传了才覆盖」。
   * 后者会让第二次调用悄悄留着上一次的 provider —— 于是「我明明没配设备头」
   * 和「实际带着上一次那个」同时成立，而这种状态没有任何办法从代码上看出来。
   */
  deviceTokenProvider = options.ensureDeviceToken ?? (() => Promise.resolve(null))
}

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use(async (config) => {
  const token = tokenProvider()
  if (token !== null && token !== '') {
    // header 名与 scheme 对齐 solvela-app 的 solvela.app.auth 配置
    config.headers.Authorization = `Bearer ${token}`
  }

  /*
   * 🔴 领设备身份的那条请求自己不能带设备头，也不能在这里等设备 ——
   * 它就是那个正在被等的东西，等它等于死锁。
   * 后端对这条路由标了 @DeviceExempt，正是同一件事在服务端的表达。
   */
  if (config.url !== DEVICE_REGISTER_URL) {
    /*
     * await 的代价只落在冷启动的头几个请求上：设备令牌一旦存进 localStorage，
     * 之后每次都是同步读。
     *
     * 而不 await 的代价是永久的 —— 首屏那几个请求会一直没有设备号，
     * 服务端那个「设备令牌覆盖率」指标就永远上不到 100%，
     * 而它正是决定「能不能从 observe 切到 enforce」的唯一依据。
     */
    const deviceToken = await deviceTokenProvider()
    if (deviceToken !== null && deviceToken !== '') {
      config.headers[DEVICE_HEADER] = deviceToken
    }
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

/**
 * 发请求并直接拿到业务数据（没有信封这一层）。
 *
 * <h3>🔴 空响应体要归一成 null</h3>
 * 后端返回 {@code null} 时，HTTP 上是 <b>200 + Content-Length: 0 + 没有 Content-Type</b>；
 * axios 拿不到可解析的东西，于是 {@code response.data} 是<b>空字符串</b>而不是 null。
 *
 * <p>后果很隐蔽：接口签名写着 {@code Promise<T | null>}，TypeScript 也认，
 * 页面照着写 {@code data === null} 的判空 —— 而那个判据<b>永远不成立</b>。
 * 于是代码走进「有数据」分支，对着一个空字符串读属性，
 * 整个组件在 render 里抛 TypeError。
 *
 * <p>它不会在联调里被发现，因为只有<b>后端真的返回 null</b> 那一次才触发；
 * 而那通常是「这个活动还没配玩法」这类正常的运营空态。
 * 彩票活动页就是这么白屏的（2026-09-21）：骨架屏卡在那儿不动，
 * 因为 Vue 在 patch 中途抛了，DOM 根本没走出加载态。
 *
 * <p>⚠️ 这里归一的是「<b>压根没有响应体</b>」。真要返回一个空字符串当数据的接口
 * 不在本项目里 —— 那种也应该返回 {@code {"value": ""}} 而不是裸字符串。
 * 没有响应体的接口走 {@link requestVoid}。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<T>(config)
  return (response.data === '' ? null : response.data) as T
}

/** 用于 204 之类没有响应体的接口 */
export async function requestVoid(config: AxiosRequestConfig): Promise<void> {
  await http.request(config)
}

export default http
