/**
 * C 端错误契约。与后端 solvela-app 的 ApiErrors 枚举一一对应。
 *
 * 后端的约定（见 ApiErrorResponse / ApiErrors 的注释）：
 *   成功 = 2xx + **数据本身**（没有 {code,msg,data} 信封）
 *   失败 = 4xx/5xx + { code, message, traceId }
 *
 * 所以客户端不要写 `if (res.data.code === 0)` 那一套 —— 判断依据是 HTTP 状态码，
 * 分支依据是 code，展示给用户的是 message。
 */

/** 后端 ApiErrors 枚举名。**改名等于改接口契约**，两边要一起改 */
export const API_ERROR_CODES = [
  'LOGIN_REQUIRED',
  'BAD_CREDENTIALS',
  /**
   * 没有有效的设备令牌。服务端 device.mode 切到 enforce 之后才会出现 ——
   * 正常情况下客户端启动时就领过了，见 api/device.ts。
   */
  'DEVICE_REQUIRED',
  /**
   * 🔴 这台设备处在**观察档**，登录要多验一道验证码。
   *
   * **它不是登录失败**：密码是对的，只是还差一步。当成失败处理的话，
   * 用户会被退回登录页从头再来，而他每次都会走到同一个地方 ——
   * 那正是「用户卡死但服务端一切正常」。
   */
  'DEVICE_VERIFICATION_REQUIRED',
  'ACCOUNT_DISABLED',
  'FORBIDDEN',
  /**
   * 🔴 在一台新设备上加收货地址 / 充话费，要先过一次邮箱验证码。
   *
   * **它不是失败**：身份有效、操作合法，只是还差一步。http.ts 会接住它、弹验证框、
   * 验证通过后自动重试原请求 —— 页面代码不用为它写任何分支。
   * 403 而不是 401：401 在这里的语义是「身份没了」，会被当成要重新登录。
   */
  'STEP_UP_REQUIRED',
  'OPERATION_LIMITED',
  'INVALID_ARGUMENT',
  'NOT_FOUND',
  'CONFLICT',
  'INTERNAL',
] as const

export type ApiErrorCode = (typeof API_ERROR_CODES)[number]

/** 本地补的码：请求根本没到服务端（断网、超时、被拦截） */
export const NETWORK_ERROR_CODE = 'NETWORK' as const

export type ErrorCode = ApiErrorCode | typeof NETWORK_ERROR_CODE

export interface ApiErrorResponse {
  code: string
  message: string
  traceId: string
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const body = value as Record<string, unknown>
  return typeof body.code === 'string' && typeof body.message === 'string'
}

/**
 * 统一的错误对象。业务代码 catch 到的永远是它，不是 AxiosError。
 *
 * traceId 一定要在报错提示里露出来 —— 后端把它塞进响应就是为了让用户截图报障时能一次定位到日志。
 */
export class ApiError extends Error {
  readonly code: ErrorCode
  readonly traceId: string | null
  readonly status: number | null

  constructor(code: ErrorCode, message: string, traceId: string | null, status: number | null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
    this.status = status
  }

  /** 是否是「没有有效身份、需要重新登录」。注意 BAD_CREDENTIALS 同为 401 但**不属于**此类 */
  get isLoginRequired(): boolean {
    return this.code === 'LOGIN_REQUIRED'
  }

  /** 是否是「在这台设备上要先过一次二次验证」。见 API_ERROR_CODES 里的说明 */
  get isStepUpRequired(): boolean {
    return this.code === 'STEP_UP_REQUIRED'
  }
}

/**
 * 把任意异常/响应体收敛成 {@link ApiError}。
 *
 * 未知的 code 一律落到 INTERNAL，但**保留后端原始 message** ——
 * 后端加了新错误码而前端还没跟上时，用户至少还能看到人话，而不是「未知错误」。
 */
export function toApiError(
  status: number | null,
  body: unknown,
  fallbackMessage: string,
): ApiError {
  if (isApiErrorResponse(body)) {
    const known = (API_ERROR_CODES as readonly string[]).includes(body.code)
    return new ApiError(
      known ? (body.code as ApiErrorCode) : 'INTERNAL',
      body.message,
      typeof body.traceId === 'string' ? body.traceId : null,
      status,
    )
  }
  return new ApiError(
    status === null ? NETWORK_ERROR_CODE : 'INTERNAL',
    fallbackMessage,
    null,
    status,
  )
}
