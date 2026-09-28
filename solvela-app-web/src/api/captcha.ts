import type { TrackPoint } from '@/utils/slider'

import { request } from './http'

/**
 * 滑块验证码（后端 CaptchaController）。
 *
 * 不需要页面直接调：受保护的接口回 CAPTCHA_REQUIRED 时，http.ts 会唤起 CaptchaDialog，
 * 由它来取图、判题，拿到通行票后原请求自动重试。
 */

export interface CaptchaChallenge {
  captchaId: string
  /** 背景 PNG（base64，不带 data: 前缀） */
  background: string
  /** 拼图 PNG（base64，透明底） */
  piece: string
  /** 拼图所在的行（原图坐标） */
  pieceY: number
  /** 原图尺寸与拼图边长。前端按显示宽度等比缩放，提交时换算回原图坐标 */
  width: number
  height: number
  pieceSize: number
}

/** 取一张图。答案只在服务端，这里拿不到 */
export async function createCaptcha(): Promise<CaptchaChallenge> {
  return request<CaptchaChallenge>({ url: '/captcha', method: 'POST', data: {} })
}

/**
 * 判题。拖对了返回通行票；拖错抛 CAPTCHA_FAILED（这张图也随之作废，要换一张）。
 *
 * @param x 拼图左边缘在【原图坐标】里的位置
 * @param track 拖动轨迹。不带或不像人手拖的，服务端和拖错一样处理
 */
export async function verifyCaptcha(
  captchaId: string,
  x: number,
  track: TrackPoint[],
): Promise<string> {
  const view = await request<{ captchaToken: string }>({
    url: '/captcha/verify',
    method: 'POST',
    data: { captchaId, x: Math.round(x), track },
  })
  return view.captchaToken
}
