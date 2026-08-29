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
