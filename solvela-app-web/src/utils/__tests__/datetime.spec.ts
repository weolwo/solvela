import { describe, expect, it } from 'vitest'

import { toDateTime } from '@/types/contract'
import { formatDateTime, isValidDateTime, parseDateTime, splitCountdown } from '@/utils/datetime'

describe('datetime', () => {
  it('能解析后端的无时区格式', () => {
    const parsed = parseDateTime(toDateTime('2026-08-29 20:00:00'))
    expect(parsed.isValid()).toBe(true)
    expect(parsed.hour()).toBe(20)
  })

  it('严格模式拒绝 ISO 等其它格式，避免悄悄接受非契约输入', () => {
    expect(isValidDateTime('2026-08-29T20:00:00')).toBe(false)
    expect(isValidDateTime('2026/08/29 20:00:00')).toBe(false)
    expect(isValidDateTime('2026-08-29 20:00:00')).toBe(true)
  })

  it('非法时间格式化成空串而不是 Invalid Date', () => {
    expect(formatDateTime(toDateTime('not a date'))).toBe('')
  })
})

describe('splitCountdown', () => {
  it('拆分剩余秒数', () => {
    expect(splitCountdown(90_061)).toEqual({
      days: 1,
      hours: 1,
      minutes: 1,
      seconds: 1,
      finished: false,
    })
  })

  it('负数收敛到 0 并标记结束', () => {
    expect(splitCountdown(-5)).toEqual({
      days: 0,
      hours: 0,
      minutes: 0,
      seconds: 0,
      finished: true,
    })
  })
})
