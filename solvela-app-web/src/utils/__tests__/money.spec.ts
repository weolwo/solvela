import { describe, expect, it } from 'vitest'

import { add, compare, format, formatWithSeparator, money, mul, sub } from '@/utils/money'

describe('money', () => {
  it('加法不产生浮点误差', () => {
    // 原生 0.1 + 0.2 === 0.30000000000000004
    expect(add(money('0.1'), money('0.2'))).toBe('0.3')
  })

  it('减法不产生浮点误差', () => {
    expect(sub(money('1.00'), money('0.9'))).toBe('0.1')
  })

  it('大额乘法保持精度', () => {
    expect(mul(money('12345678901234.56'), 3)).toBe('37037036703703.68')
  })

  it('展示格式化向下取整，不四舍五入', () => {
    // 余额显示得比实际多一分是客诉，少一分不是
    expect(format(money('99.999'))).toBe('99.99')
    expect(format(money('0.005'))).toBe('0.00')
  })

  it('千分位格式化', () => {
    expect(formatWithSeparator(money('12345.678'))).toBe('12,345.67')
    expect(formatWithSeparator(money('999'))).toBe('999.00')
    expect(formatWithSeparator(money('1234567.8'))).toBe('1,234,567.80')
  })

  it('比较用十进制语义而非字符串字典序', () => {
    expect(compare(money('10'), money('9'))).toBe(1)
  })
})
