import { request, requestVoid } from './http'

import type { DeviceType } from './auth'

/**
 * 账号安全的「反馈闭环」：在<b>老设备</b>上确认刚才那次新设备登录是不是本人。
 *
 * <h3>它不是通知，是提问</h3>
 * 新设备登录的邮件和站内信只是告知，答不回来。而风控最缺的恰恰是答案 ——
 * 没有「是我 / 不是我」，「打扰了多少本人、漏过了多少盗号」只能靠猜。
 *
 * <h3>🔴 服务端只在受信任的老设备上返回待确认的</h3>
 * 所以这个接口在新设备上返回的永远是空数组 —— 那不是错误，不要当成失败处理。
 * 盗号者就在那台新设备上，在那里问他会点「是我」。
 */

export interface SecurityPrompt {
  /** 答题凭它。一次性 */
  promptId: string
  /** 拿不到时为 null，显示「未知设备」而不是留白 —— 留白像是页面坏了 */
  deviceType: DeviceType | null
  /** IP 归属地（人话，不是 IP）。拿不到时为 null */
  location: string | null
  /** 那次登录的时间（毫秒） */
  loginTime: number
}

/** 待确认列表。空数组是常态（当前不是受信任的老设备） */
export async function fetchSecurityPrompts(): Promise<SecurityPrompt[]> {
  return request<SecurityPrompt[]>({ url: '/auth/security/prompts', method: 'POST' })
}

/**
 * 回答一条。返回 204。
 *
 * 🔴 `isMe === false` 等同「这不是我」：服务端会让那台设备下线、把其它设备也登出，
 * 并**冻结兑换、加地址、充话费**到改密为止。所以这颗按钮要和「是我」在视觉上分清，
 * 而且答完要引导去重置密码。
 */
export async function answerSecurityPrompt(promptId: string, isMe: boolean): Promise<void> {
  await requestVoid({
    url: '/auth/security/prompts/answer',
    method: 'POST',
    data: { promptId, isMe },
  })
}
