import { describe, expect, it } from 'vitest'

import { toId } from '@/types/contract'

describe('toId', () => {
  // LongJsonSerializer 在安全整数范围内输出数字，超出输出字符串。
  // 这两条用例钉住的就是「同一字段两种 JSON 类型」这个契约。
  it('把后端下发的数字主键归一成字符串', () => {
    expect(toId(1024)).toBe('1024')
  })

  it('原样保留后端下发的字符串主键', () => {
    expect(toId('1934820293847562341')).toBe('1934820293847562341')
  })

  it('遇到已经丢精度的数字主键要报错，而不是继续往下传', () => {
    expect(() => toId(Number.MAX_SAFE_INTEGER + 2)).toThrow(RangeError)
  })
})
