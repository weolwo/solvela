import Decimal from 'decimal.js'

import type { Money } from '@/types/contract'

/**
 * 金额运算。**任何涉及钱的地方都必须经过这里，不许用 JS 原生算术。**
 *
 * 后端 JsonConfig 给 BigDecimal 挂了 ToStringSerializer，金额到前端永远是十进制字符串。
 * 这不是可以随手 Number() 掉的东西 —— 0.1 + 0.2 那类误差在钱包和中奖金额上就是事故。
 */

// 钱不允许出现科学计数法，否则 "1e-7" 这种字符串会直接展示给用户
Decimal.set({ toExpNeg: -30, toExpPos: 30 })

export const ZERO = '0' as Money

export function money(raw: string | Money): Money {
  return raw as Money
}

function d(value: Money): Decimal {
  return new Decimal(value)
}

export function add(a: Money, b: Money): Money {
  return d(a).plus(d(b)).toFixed() as Money
}

export function sub(a: Money, b: Money): Money {
  return d(a).minus(d(b)).toFixed() as Money
}

export function mul(a: Money, times: number | string): Money {
  return d(a).times(times).toFixed() as Money
}

export function compare(a: Money, b: Money): -1 | 0 | 1 {
  return d(a).comparedTo(d(b)) as -1 | 0 | 1
}

export function isZero(value: Money): boolean {
  return d(value).isZero()
}

export function isNegative(value: Money): boolean {
  return d(value).isNegative()
}

/**
 * 展示用格式化。**向下取整**（ROUND_DOWN）而不是四舍五入：
 * 余额、可提现金额这类数字宁可显示得比实际少一分，也不能显示得比实际多 —— 后者是客诉。
 */
export function format(value: Money, decimalPlaces = 2): string {
  return d(value).toFixed(decimalPlaces, Decimal.ROUND_DOWN)
}

/** 带千分位的展示，例：1,234.56 */
export function formatWithSeparator(value: Money, decimalPlaces = 2): string {
  const fixed = format(value, decimalPlaces)
  const [intPart = '0', fracPart] = fixed.split('.')
  const withSeparator = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return fracPart === undefined ? withSeparator : `${withSeparator}.${fracPart}`
}
