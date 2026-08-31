import { type Id, toId } from '@/types/contract'

import { request, requestVoid } from './http'

/** 设备端，取值对齐 t_member_login_log.device_type */
export const DEVICE_TYPES = ['APP', 'H5', 'WECHAT', 'PC'] as const
export type DeviceType = (typeof DEVICE_TYPES)[number]

export interface LoginPayload {
  phone: string
  password: string
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

export interface RegisterPayload {
  phone: string
  password: string
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

/**
 * 注册。**返回形状与登录完全一致**，所以调用方走同一条「存令牌 + 存会员信息」的路。
 *
 * 后端注册成功直接签令牌（见 MemberLoginController.register 的注释）——
 * 没有「注册完再登一次」这一步，那一步不产生任何信息，只多一次可能失败的调用。
 *
 * 失败时抛 ApiError，几个码各有含义，注册页据此分支：
 *   CONFLICT(409)         手机号已注册 → 引导去登录，不要只显示一行红字
 *   INVALID_ARGUMENT(400) 手机号格式错 / 密码太弱 → message 就是规则原文，直接展示
 *   OPERATION_LIMITED(429) 同一 IP 注册过于频繁 → message 里已带「还要等多久」
 */
export async function register(payload: RegisterPayload): Promise<LoginResult> {
  const raw = await request<RawLoginResult>({
    url: '/auth/register',
    method: 'POST',
    data: { deviceType: 'H5', ...payload },
  })
  return {
    accessToken: raw.accessToken,
    expiresIn: raw.expiresIn,
    member: normalizeMember(raw.member),
  }
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const raw = await request<RawLoginResult>({
    url: '/auth/login',
    method: 'POST',
    data: { deviceType: 'H5', ...payload },
  })
  return {
    accessToken: raw.accessToken,
    expiresIn: raw.expiresIn,
    member: normalizeMember(raw.member),
  }
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
