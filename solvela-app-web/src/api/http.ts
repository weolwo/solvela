import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'

import { toApiError } from './errors'

declare module 'axios' {
  interface AxiosRequestConfig {
    /**
     * 这条请求已经因为 STEP_UP_REQUIRED 验证过一次、正在重试。
     *
     * 🔴 有它才不会死循环：验证通过后重试仍然拿到 STEP_UP_REQUIRED（比如服务端没记住设备），
     * 不带这个标记的话会再弹一次框、再重试一次，无穷无尽。
     */
    stepUpRetried?: boolean
  }
}

/**
 * HTTP 客户端。
 *
 * 四条约定，改之前先看 errors.ts 的说明：
 *   1. 成功时 `response.data` **就是业务数据**，没有信封要剥。
 *   2. 失败时抛 {@link ApiError}，业务代码不接触 AxiosError。
 *   3. 只有 LOGIN_REQUIRED 触发「清会话 + 跳登录」。
 *      BAD_CREDENTIALS 同样是 401，但它是「这次密码输错了」，必须原样抛给登录页，
 *      否则用户会被弹回登录页而看不到「手机号或密码错误」这句提示。
 *   4. 🔴 **凭证不经过这里。** 会话令牌与设备令牌都在服务端下发的 HttpOnly cookie 里，
 *      同源请求由浏览器自动带上，页面脚本读不到、也不需要读。
 *      以前它们存在 localStorage、由这里塞进 Authorization / X-Device-Token 头 ——
 *      那意味着混进页面的任何一段脚本都能把它们读走，带回自己的机器长期使用。
 *      原理见 docs/知识库/Web鉴权-Cookie与浏览器安全边界.md。
 */

type UnauthorizedHandler = () => void
type DeviceEnsurer = () => Promise<void>
/** 弹出二次验证、等用户完成。resolve(true) = 验证通过，调用方重试原请求；false = 用户放弃 */
type StepUpHandler = () => Promise<boolean>

let unauthorizedHandler: UnauthorizedHandler = () => {}
/** 默认不做：注入之前（比如单测里）行为退化成「没有设备身份」，而不是报错 */
let deviceEnsurer: DeviceEnsurer = () => Promise.resolve()
/** 默认「不处理」：注入之前（单测里、未登录）STEP_UP_REQUIRED 原样抛给调用方 */
let stepUpHandler: StepUpHandler = () => Promise.resolve(false)

/**
 * 领设备身份的那条路由。
 *
 * 🔴 常量放在 http.ts 而不是 device.ts，是为了**避免循环引用**：
 * device.ts 要用 http 发请求，http 的拦截器又要认出这条路由并跳过它。
 * 方向做成 device → http 这一条，就没有环。
 */
export const DEVICE_REGISTER_URL = '/device/register'

/** 由 stores/auth 在初始化时注入，避免 http 反向依赖 store 造成循环引用 */
export function configureHttp(options: {
  onLoginRequired: UnauthorizedHandler
  /**
   * 可选：确保这个浏览器已经有设备身份（HttpOnly cookie）。**不传就是「不管」**，不是「沿用上一次」。
   *
   * 每个请求发出前都会 await 它 —— 实现方必须把结果缓存住，只有冷启动那一次真的发请求。
   */
  ensureDevice?: DeviceEnsurer
  /**
   * 可选：服务端要求二次验证时怎么办。**不传就是「不处理，原样抛给调用方」**。
   *
   * 见 {@link handleStepUp}：多个请求同时撞上时只弹一次框，验证通过后各自重试。
   */
  onStepUpRequired?: StepUpHandler
}): void {
  unauthorizedHandler = options.onLoginRequired
  /*
   * 🔴 无条件赋值，不写成「传了才覆盖」。
   * 后者会让第二次调用悄悄留着上一次的实现 —— 于是「我明明没配」
   * 和「实际还在用上一次那个」同时成立，而这种状态没有任何办法从代码上看出来。
   */
  deviceEnsurer = options.ensureDevice ?? (() => Promise.resolve())
  // 同上：无条件赋值，不传就是回到「不处理」
  stepUpHandler = options.onStepUpRequired ?? (() => Promise.resolve(false))
}

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use(async (config) => {
  /*
   * 🔴 领设备身份的那条请求自己不能等设备 —— 它就是那个正在被等的东西，等它等于死锁。
   * 后端对这条路由标了 @DeviceExempt，正是同一件事在服务端的表达。
   */
  if (config.url !== DEVICE_REGISTER_URL) {
    /*
     * 等设备 cookie 落地再发：注册、登录、活动页这些匿名请求也要带着设备身份 ——
     * 防刷要防的正是它们。代价只落在冷启动的头几个请求上（结果由 ensureDevice 缓存）。
     */
    await deviceEnsurer()
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

    /*
     * 二次验证：弹框 → 用户验证通过 → 原样重试。调用方完全感知不到中间这一段，
     * 它 await 到的就是重试之后的结果 —— 页面代码一行都不用为二次验证改。
     */
    const config = error.config
    if (apiError.isStepUpRequired && config !== undefined && config.stepUpRetried !== true) {
      return handleStepUp().then((verified) =>
        verified ? http.request({ ...config, stepUpRetried: true }) : Promise.reject(apiError),
      )
    }

    return Promise.reject(apiError)
  },
)

/**
 * 在途的那一次二次验证。
 *
 * 🔴 没有它的话，一个页面同时发出的两个请求（比如保存地址后顺手刷新列表时又撞上一次）
 * 会**各弹一个框**、各发一封验证码 —— 用户收到两封信，输了第一封的码，第二个框还挂着。
 * 共用同一个 promise：只弹一次，验证通过后所有等着的请求各自重试。
 */
let stepUpInflight: Promise<boolean> | null = null

function handleStepUp(): Promise<boolean> {
  if (stepUpInflight === null) {
    stepUpInflight = stepUpHandler()
      .catch(() => false)
      .finally(() => {
        stepUpInflight = null
      })
  }
  return stepUpInflight
}

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
