import { type Id, toId } from '@/types/contract'

import { request, requestVoid } from './http'

/** 设备端，取值对齐 t_member_login_log.device_type */
export const DEVICE_TYPES = ['APP', 'H5', 'WECHAT', 'PC'] as const
export type DeviceType = (typeof DEVICE_TYPES)[number]

/**
 * 登录方式。对齐后端 MemberLoginType。
 *
 * `identity` 和 `credential` 装什么，由它决定：
 *   PHONE_PASSWORD  手机号 + 密码
 *   EMAIL_PASSWORD  邮箱   + 密码
 *   EMAIL_CODE      邮箱   + 邮箱验证码
 */
export const LOGIN_TYPES = ['PHONE_PASSWORD', 'EMAIL_PASSWORD', 'EMAIL_CODE'] as const
export type LoginType = (typeof LOGIN_TYPES)[number]

/** 注册方式。对齐后端 MemberRegisterType */
export const REGISTER_TYPES = ['PHONE_PASSWORD', 'EMAIL_CODE'] as const
export type RegisterType = (typeof REGISTER_TYPES)[number]

/**
 * 登录入参。
 *
 * 🔴 字段叫 `identity` / `credential`，**不是** `phone` / `password`。
 * 后端 2026-09-09 改的名，理由写在 MemberLoginRequest 的注释里：
 * 「继续叫 phone 但有时候放的是邮箱」是一个迟早会骗到人的字段名。
 */
export interface LoginPayload {
  loginType?: LoginType
  identity: string
  credential: string
  /**
   * 二次验证码。**只在服务端回过 DEVICE_VERIFICATION_REQUIRED 之后才需要带**。
   *
   * 与 `credential` 不是一回事：credential 是「你知道什么」（密码），
   * 这一项是「这台设备最近可疑，再证明一次你能收到本人的短信」。
   *
   * 客户端不必先问一次「要不要验」—— 先不带地提交，被回绝了再补。
   * 多一次往返，换的是绝大多数登录不受影响。
   *
   * 🔴 类型带上 `| undefined` 不是啰嗦：tsconfig 开了 exactOptionalPropertyTypes，
   * 在那个开关下「没传这个字段」和「传了 undefined」是两件事，
   * 而调用方最自然的写法就是 `verificationCode: code || undefined`。
   */
  verificationCode?: string | undefined
  deviceType?: DeviceType
}

/**
 * 当前登录会员。对应后端 MemberPrincipal。
 *
 * **这里没有手机号是后端刻意的**：该对象会进 Redis、进日志，放明文手机号会让整套 PII 加密失效。
 * 需要展示手机号的页面走单独接口，拿的是脱敏后的值。
 */
export interface MemberProfile {
  memberId: Id
  memberName: string
  nickname: string
  /** 头像 file_id，可能为空 */
  avatarFileId: Id | null
  /** 0-未知 1-男 2-女 */
  gender: number | null
}

/**
 * 注册入参。
 *
 * 两条通道共用这一个形状，由 `registerType` 分派：
 *   PHONE_PASSWORD  identity=手机号，要 smsCode 和 password
 *   EMAIL_CODE      identity=邮箱，  要 emailCode，password 可以不填
 *
 * `smsCode` **一直传**就行：服务端有一个 `phone-code-required` 开关，
 * 关掉时它不看这个字段，多传一个没有代价；而漏传会在开关打开的那天变成注册全线失败。
 */
export interface RegisterPayload {
  registerType?: RegisterType
  identity: string
  /** 邮箱注册的验证码 */
  emailCode?: string | undefined
  /** 手机号注册的短信验证码 */
  smsCode?: string | undefined
  /**
   * 邮箱注册时可以不填 —— 那种会员之后走验证码登录。
   *
   * 🔴 不填要传 `undefined`，**不能传空串**：空串会被后端当成
   * 「填了一个不合规的密码」而拒掉（见 MemberRegisterService 的强度校验）。
   *
   * 类型带 `| undefined` 是必须的：tsconfig 开了 exactOptionalPropertyTypes，
   * 在那个开关下「没传这个字段」和「传了 undefined」是两件事。
   */
  password?: string | undefined
  deviceType?: DeviceType
}

export interface LoginResult {
  accessToken: string
  /** 有效期秒数。用来提前续期，而不是等 401 才反应 */
  expiresIn: number
  member: MemberProfile
}

/** 后端原始形状：memberId / avatarFileId 是 Long，小值下发为数字 */
interface RawMemberProfile {
  memberId: string | number
  memberName: string
  nickname: string
  avatarFileId: string | number | null
  gender: number | null
}

interface RawLoginResult {
  accessToken: string
  expiresIn: number
  member: RawMemberProfile
}

/** 反序列化边界：所有 Long 字段在这里归一成字符串，往后不再出现 number 型 ID */
function normalizeMember(raw: RawMemberProfile): MemberProfile {
  return {
    memberId: toId(raw.memberId),
    memberName: raw.memberName,
    nickname: raw.nickname,
    avatarFileId: raw.avatarFileId === null ? null : toId(raw.avatarFileId),
    gender: raw.gender,
  }
}

function toLoginResult(raw: RawLoginResult): LoginResult {
  return {
    accessToken: raw.accessToken,
    expiresIn: raw.expiresIn,
    member: normalizeMember(raw.member),
  }
}

/**
 * 注册。**返回形状与登录完全一致**，所以调用方走同一条「存令牌 + 存会员信息」的路。
 *
 * 后端注册成功直接签令牌（见 MemberLoginController.register 的注释）——
 * 没有「注册完再登一次」这一步，那一步不产生任何信息，只多一次可能失败的调用。
 *
 * 失败时抛 ApiError，几个码各有含义，注册页据此分支：
 *   CONFLICT(409)          手机号/邮箱已注册 → 引导去登录，不要只显示一行红字
 *   INVALID_ARGUMENT(400)  格式错 / 密码太弱 → message 就是规则原文，直接展示
 *   BAD_CREDENTIALS(401)   验证码错、失效、错太多次 → 挂在验证码框上
 *   OPERATION_LIMITED(429) 注册过于频繁 → message 里已带「还要等多久」
 */
export async function register(payload: RegisterPayload): Promise<LoginResult> {
  const raw = await request<RawLoginResult>({
    url: '/auth/register',
    method: 'POST',
    data: { registerType: 'PHONE_PASSWORD', deviceType: 'H5', ...payload },
  })
  return toLoginResult(raw)
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const raw = await request<RawLoginResult>({
    url: '/auth/login',
    method: 'POST',
    data: { loginType: 'PHONE_PASSWORD', deviceType: 'H5', ...payload },
  })
  return toLoginResult(raw)
}

/**
 * 短信验证码的用途。对齐后端 SmsScene。
 *
 * 比邮箱**少一个 BIND** —— 绑定手机号还没做。
 *
 * 🔴 场景必须显式传，服务端不给默认值：默认成注册的话，
 * 用户拿去重置密码时验不过，而两边看起来都很正常。
 */
export const SMS_SCENES = ['REGISTER', 'LOGIN', 'RESET_PASSWORD', 'BIND'] as const
export type SmsScene = (typeof SMS_SCENES)[number]

/** 邮箱验证码的用途。对齐后端 EmailCodeScene */
export const EMAIL_CODE_SCENES = ['REGISTER', 'LOGIN', 'BIND', 'RESET_PASSWORD'] as const
export type EmailCodeScene = (typeof EMAIL_CODE_SCENES)[number]

/**
 * 索取短信验证码。成功返回 204，**没有响应体**。
 *
 * <p>⚠️ 短信是**要花钱**的接口，服务端的 IP 日限比邮箱紧得多。
 * 所以这颗按钮必须有冷却（见 useCodeSender），不能让用户连点。
 *
 * <p>失败时抛 ApiError：
 *   INVALID_ARGUMENT(400)  号码格式不对
 *   OPERATION_LIMITED(429) 冷却中 / 今天发太多了，message 里已带「还要等多久」
 *   INTERNAL(500)          发不出去 —— 这是**我们的**问题，别让用户以为号码填错了
 */
export async function sendSmsCode(scene: SmsScene, phone: string): Promise<void> {
  await requestVoid({ url: '/auth/sms/code', method: 'POST', data: { scene, phone } })
}

/**
 * 索取邮箱验证码。成功返回 204，**没有响应体**。
 *
 * <p>🔴 **成功不代表真的寄了一封信。** 邮箱与场景不匹配时（拿一个没注册过的邮箱
 * 要登录验证码、拿一个已注册的邮箱要注册验证码）服务端会静默成功 ——
 * 如实回答等于送出一个账号枚举接口。
 *
 * <p>所以调用方**不要**把成功解释成「这个邮箱存在/不存在」，
 * 提示语只能是「已发送」这一句，不能有第二种。
 */
export async function sendEmailCode(scene: EmailCodeScene, email: string): Promise<void> {
  await requestVoid({ url: '/auth/email/code', method: 'POST', data: { scene, email } })
}

/**
 * 我的联系方式，**全部脱敏**（形如 `138****8000`）。
 *
 * 🔴 这是单独一次调用，不在 `/auth/me` 里 —— 那个结果会进网关缓存和日志，
 * 而手机号邮箱是 PII。明文一次都不会出域，这里拿到的就是最终要显示的样子。
 */
export interface MemberContact {
  phone: string | null
  email: string | null
  /**
   * 有没有设过密码。
   *
   * 换绑邮箱的界面靠它决定给什么选项：没设过密码的人只能走「旧邮箱验证码」，
   * 给他一个「输入当前密码」的框，是让他对着一个填不了的东西发愁。
   */
  passwordSet: boolean
}

export async function fetchContact(): Promise<MemberContact> {
  return request<MemberContact>({ url: '/auth/contact', method: 'POST' })
}

/**
 * 绑定 / 更换邮箱。成功返回 204。
 *
 * <h3>🔴 换绑必须多给一样东西，首次绑定不用</h3>
 * 拦的是这条链：**会话被盗 → 换绑成攻击者的邮箱 → 用「忘记密码」重置 → 永久接管**。
 * 每一步单看都合法，而 C 端令牌有 30 天有效期。
 *
 * 所以已经绑过邮箱的人，还要证明自己是原主 —— `currentPassword`
 * 或 `oldEmailCode` 二选一。没设过密码的会员只有后一条路。
 */
export interface EmailBindPayload {
  email: string
  /** 新邮箱收到的验证码 */
  code: string
  /** 当前密码。换绑时与 oldEmailCode 二选一 */
  currentPassword?: string | undefined
  /** 旧邮箱收到的验证码。换绑时与 currentPassword 二选一 */
  oldEmailCode?: string | undefined
}

export async function bindEmail(payload: EmailBindPayload): Promise<void> {
  await requestVoid({ url: '/auth/email/bind', method: 'POST', data: payload })
}

/**
 * 绑定 / 更换手机号。成功返回 204。
 *
 * <h3>🔴 与绑定邮箱同一条规矩，而且手机号更重</h3>
 * `uk_mbr_phone_hash` 是唯一约束，手机号还是注册的默认身份 ——
 * 换绑意味着「原来那个号从此登不了、也注册不了这个账号」。
 * 所以换绑要多证明一次「你是原主」：`currentPassword` 或 `oldPhoneCode` 二选一。
 */
export interface PhoneBindPayload {
  phone: string
  /** 新手机号收到的验证码 */
  code: string
  currentPassword?: string | undefined
  /** 旧手机号收到的验证码。没设过密码的会员只有这一条路 */
  oldPhoneCode?: string | undefined
}

export async function bindPhone(payload: PhoneBindPayload): Promise<void> {
  await requestVoid({ url: '/auth/phone/bind', method: 'POST', data: payload })
}

/**
 * 用邮箱验证码重置密码。**匿名接口** —— 用户正是因为进不去才走这条路。
 *
 * <p>🔴 成功之后他在**所有设备**上的会话都会被吊销，返回被吊销的数量。
 * 这个数字要展示出来：点「忘记密码」的最常见原因之一就是「我怀疑号被人动过」，
 * 而「已在 3 台设备上退出登录」正是他要的那个答案。只回一句「修改成功」，
 * 这条信息就白丢了。
 */
export interface PasswordResetResult {
  revokedSessions: number
}

/**
 * 找回方式。与 `LoginType` / `RegisterType` 一个路数：
 * 一个 `identity` 字段 + 一个类型枚举，而不是并排两个 email / phone 字段。
 */
export const PASSWORD_RESET_TYPES = ['SMS_CODE', 'EMAIL_CODE'] as const
export type PasswordResetType = (typeof PASSWORD_RESET_TYPES)[number]

export async function resetPassword(payload: {
  resetType: PasswordResetType
  /** 邮箱或手机号。**刻意不叫 email** —— 「继续叫 email 但有时候放的是手机号」是个会骗人的字段名 */
  identity: string
  code: string
  newPassword: string
}): Promise<PasswordResetResult> {
  return request<PasswordResetResult>({
    url: '/auth/password/reset',
    method: 'POST',
    data: payload,
  })
}

/** 后端返回 204，没有响应体 */
export async function logout(): Promise<void> {
  await requestVoid({ url: '/auth/logout', method: 'POST' })
}

/** 冷启动时确认本地令牌是否还有效。注意是 POST，不是 GET */
export async function fetchMe(): Promise<MemberProfile> {
  const raw = await request<RawMemberProfile>({ url: '/auth/me', method: 'POST' })
  return normalizeMember(raw)
}
