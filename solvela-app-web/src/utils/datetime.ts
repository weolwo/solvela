import dayjs, { type Dayjs } from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'

import type { DateString, DateTimeString } from '@/types/contract'

dayjs.extend(customParseFormat)

/**
 * 日期时间处理。
 *
 * 后端 JsonConfig 把 LocalDateTime 序列化成 `yyyy-MM-dd HH:mm:ss`，**没有时区也没有 T**。
 * 两个后果：
 *
 * 1. 不能 `new Date('2026-08-29 20:00:00')` —— 各浏览器对这个非 ISO 格式的解析行为不一致。
 *    一律用本模块的 {@link parseDateTime}，显式指定格式。
 *
 * 2. 字符串里没有偏移量，客户端只能按本机时区解释它。
 *    **所以倒计时绝对不能用「服务端时间字符串 - 本地当前时间」**：
 *    用户设备时区或时钟不对，倒计时就是错的，而抽奖类业务里这直接等于事故。
 *    倒计时请让后端下发剩余秒数，用 {@link createCountdown}。
 */

export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'
export const DATE_FORMAT = 'YYYY-MM-DD'

export function parseDateTime(value: DateTimeString): Dayjs {
  return dayjs(value, DATETIME_FORMAT, true)
}

export function parseDate(value: DateString): Dayjs {
  return dayjs(value, DATE_FORMAT, true)
}

export function isValidDateTime(value: string): boolean {
  return dayjs(value, DATETIME_FORMAT, true).isValid()
}

/** 展示用格式化，例：08-29 20:00 */
export function formatDateTime(value: DateTimeString, template = 'MM-DD HH:mm'): string {
  const parsed = parseDateTime(value)
  return parsed.isValid() ? parsed.format(template) : ''
}

export interface CountdownParts {
  days: number
  hours: number
  minutes: number
  seconds: number
  /** 是否已经走到 0 */
  finished: boolean
}

/**
 * 把「剩余秒数」拆成天/时/分/秒。
 *
 * 入参刻意是秒数而不是截止时间字符串 —— 见本文件顶部第 2 条。
 */
export function splitCountdown(remainingSeconds: number): CountdownParts {
  const total = Math.max(0, Math.floor(remainingSeconds))
  return {
    days: Math.floor(total / 86_400),
    hours: Math.floor((total % 86_400) / 3_600),
    minutes: Math.floor((total % 3_600) / 60),
    seconds: total % 60,
    finished: total === 0,
  }
}

/**
 * 基于服务端下发的剩余秒数做本地递减。
 *
 * 用 performance.now 的单调时钟算流逝时间，而不是累加 setInterval 的次数：
 * 页面切到后台时定时器会被节流，累加法会越走越慢。
 */
export function createCountdown(
  remainingSeconds: number,
  onTick: (parts: CountdownParts) => void,
): () => void {
  const startedAt = performance.now()
  let timer: ReturnType<typeof setInterval> | null = null

  const tick = (): void => {
    const elapsed = (performance.now() - startedAt) / 1000
    const parts = splitCountdown(remainingSeconds - elapsed)
    onTick(parts)
    if (parts.finished && timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  tick()
  timer = setInterval(tick, 1000)

  return () => {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }
}
