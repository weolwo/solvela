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
  /**
   * 登录二次验证的凭票失效了（过期、码错太多次被作废、换了设备）。
   * 要回到输密码那一步重新登录拿新票 —— 再点多少次「获取验证码」都没用。
   */
  'CHALLENGE_EXPIRED',
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
  /**
   * 发验证码、密码登录之前要先过一次滑块。http.ts 会接住它、弹滑块、拿到通行票后自动重试原请求。
   */
  'CAPTCHA_REQUIRED',
  /** 滑块没拖对（或图已过期）。滑块弹窗自己处理：换一张图重来 */
  'CAPTCHA_FAILED',
  'OPERATION_LIMITED',
  /**
   * 🔴 密码连续错太多次，**密码这条通道**进了阶梯冷却（60s / 5m / 10m / 60m / 24h）。
   *
   * 与 OPERATION_LIMITED 分开，因为它是唯一一个**用户此刻就有出路**的限制：
   * 验证码登录和找回密码都没被锁。所以不要只展示 message 了事 ——
   * 要弹窗 + 倒计时 + 把 `details.alternatives` 里那几条出路摆成按钮。
   *
   * `details`: {@link PasswordCooldownDetails}
   */
  'PASSWORD_COOLDOWN',
  /**
   * 要验证码，但这个账号**一条能收码的通道都没有**（手机号账号 + 短信还没接 + 没绑邮箱）。
   *
   * 🔴 **只能给「联系客服」，绝不能给「去绑定」**。此刻还没有登录态，对面这个人只证明了
   * 「知道密码」—— 让他去绑联系方式，等于让他给别人的账号加一条他自己的找回通道，
   * 之后用它重置密码就是永久接管。
   */
  'LOGIN_VERIFY_UNAVAILABLE',
  'INVALID_ARGUMENT',
  'NOT_FOUND',
  'CONFLICT',
  'INTERNAL',
  /**
   * 503：业务服务暂时连不上（多半是正在发版重启），请求**确定没有执行**，等几秒重试即可。
   * 与 INTERNAL 区分开，是为了不把「服务在重启」当成「程序出错了」。
   */
  'SERVICE_UNAVAILABLE',
] as const

export type ApiErrorCode = (typeof API_ERROR_CODES)[number]

/** 本地补的码：请求根本没到服务端（断网、超时、被拦截） */
export const NETWORK_ERROR_CODE = 'NETWORK' as const

export type ErrorCode = ApiErrorCode | typeof NETWORK_ERROR_CODE

/** 核身通道。与后端 MemberVerifyChannelEnum 对应 */
export type VerifyChannel = 'EMAIL' | 'SMS'

/** 密码冷却时还走得通的路。与后端 MemberLoginService.cooldownDetails 对应 */
export type LoginAlternative = 'CODE_LOGIN' | 'PASSWORD_RESET'

/** DEVICE_VERIFICATION_REQUIRED 带的东西 */
export interface ChallengeDetails {
  /** 之后发码、验码都凭它 */
  ticket: string
  /**
   * 码发到哪。**不等于登录方式** —— 手机号登录也可能把码发到邮箱
   * （后端的核身路由会换通道，短信没接时也会退回邮箱）。
   * 所以文案必须按它说，照登录方式猜会让用户去翻错的收件箱。
   */
  channel: VerifyChannel | null
}

/** PASSWORD_COOLDOWN 带的东西 */
export interface PasswordCooldownDetails {
  retryAfterSeconds: number
  /** 可能是空数组：手机号登录在短信接通之前两条路都走不通，那时只显示「知道了」 */
  alternatives: LoginAlternative[]
}

export interface ApiErrorResponse {
  code: string
  message: string
  traceId: string
  /**
   * 个别错误带的附加数据。绝大多数错误没有。
   *
   * 🔴 类型是 `Record<string, unknown>` 而**不是**按码区分的联合类型，这是刻意的：
   * 它来自网络，而网络上的东西没有类型。写成联合类型只是一句编译期的断言 ——
   * 后端改了字段名、或者老版本服务端还没带上某个字段时，TypeScript 什么也不会说，
   * 而页面代码会对着 undefined 读属性。
   *
   * 所以这里保持「未知」，由 {@link ApiError} 上那几个取值器**在运行时校验一次**，
   * 页面拿到的是已经验过的 `ChallengeDetails | null` 这种东西 ——
   * 既不用自己写 `typeof x === 'string'`，也没有假的类型安全。
   */
  details?: Record<string, unknown>
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
  /** 见 {@link ApiErrorResponse.details} */
  readonly details: Record<string, unknown> | null

  constructor(
    code: ErrorCode,
    message: string,
    traceId: string | null,
    status: number | null,
    details: Record<string, unknown> | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
    this.status = status
    this.details = details
  }

  /** 是否是「没有有效身份、需要重新登录」。注意 BAD_CREDENTIALS 同为 401 但**不属于**此类 */
  get isLoginRequired(): boolean {
    return this.code === 'LOGIN_REQUIRED'
  }

  /** 是否是「要先过一次滑块验证码」 */
  get isCaptchaRequired(): boolean {
    return this.code === 'CAPTCHA_REQUIRED'
  }

  /** 是否是「在这台设备上要先过一次二次验证」。见 API_ERROR_CODES 里的说明 */
  get isStepUpRequired(): boolean {
    return this.code === 'STEP_UP_REQUIRED'
  }

  /**
   * 登录二次验证的凭票与通道。不是那个码、或者票不是个字符串时返回 null。
   *
   * 票缺失时**不要**退化成「继续走下去」：没有票的话发码、验码都做不了，
   * 把验证码栏亮出来只会给用户一个点了没反应的按钮。
   */
  get challenge(): ChallengeDetails | null {
    if (this.code !== 'DEVICE_VERIFICATION_REQUIRED') {
      return null
    }
    const ticket = this.details?.challengeTicket
    if (typeof ticket !== 'string' || ticket === '') {
      return null
    }
    const channel = this.details?.channel
    return { ticket, channel: channel === 'EMAIL' || channel === 'SMS' ? channel : null }
  }

  /**
   * 还要等多少秒。429 这几个码带，其余为 null。
   *
   * 拿不到就返回 null，**不要兜一个默认值** —— 倒计时从一个编出来的数字开始走，
   * 走到 0 用户以为能点了，结果又是一次 429。
   */
  get retryAfterSeconds(): number | null {
    const seconds = this.details?.retryAfterSeconds
    return typeof seconds === 'number' && Number.isFinite(seconds) && seconds > 0
      ? Math.ceil(seconds)
      : null
  }

  /** 密码冷却时的倒计时与出路。不是那个码时返回 null */
  get passwordCooldown(): PasswordCooldownDetails | null {
    if (this.code !== 'PASSWORD_COOLDOWN') {
      return null
    }
    const raw = this.details?.alternatives
    const alternatives = Array.isArray(raw)
      ? raw.filter(
          (item): item is LoginAlternative => item === 'CODE_LOGIN' || item === 'PASSWORD_RESET',
        )
      : []
    return { retryAfterSeconds: this.retryAfterSeconds ?? 0, alternatives }
  }

  /**
   * 「再错几次就要等了」。服务端**只在剩最后一次时才给**这个数，
   * 所以它有值就意味着该把那句提醒说出来。
   */
  get attemptsBeforeCooldown(): number | null {
    const attempts = this.details?.attemptsBeforeCooldown
    return typeof attempts === 'number' && Number.isFinite(attempts) && attempts >= 0
      ? attempts
      : null
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
      typeof body.details === 'object' && body.details !== null ? body.details : null,
    )
  }
  return new ApiError(
    status === null ? NETWORK_ERROR_CODE : 'INTERNAL',
    fallbackMessage,
    null,
    status,
  )
}
