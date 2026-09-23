import { describe, expect, it } from 'vitest'

import { formatCost, formatDiscount, formatListPoints } from '@/utils/cost'
import { toMoney } from '@/types/contract'

/**
 * 等级价的**展示**规则。
 *
 * 🔴 这里守的核心是一条：**判据是两个价不相等，不是折扣率小于 100。**
 *
 * 商品可以单独退出等级折扣（`grade_price_flag=0`，成本价商品、秒杀品）。
 * 那时这个人的折扣率照样是 88 —— 按折扣率判会在一件**根本没便宜**的商品上
 * 挂出「8.8折」，用户点进去发现价格一分没少。这个错不报错、不抛异常，
 * 只是让一个促销标签变成谎话。
 */
describe('等级折扣的展示', () => {
  it('折扣率写成「几折」，整数折不带小数点', () => {
    expect(formatDiscount(8800, 10000, 88)).toBe('8.8折')
    expect(formatDiscount(9000, 10000, 90)).toBe('9折')
    expect(formatDiscount(9500, 10000, 95)).toBe('9.5折')
  })

  it('🔴 商品退出等级折扣：两个价相等，不出角标 —— 哪怕折扣率是 88', () => {
    expect(formatDiscount(10000, 10000, 88)).toBe('')
    expect(formatListPoints(10000, 10000)).toBe('')
  })

  it('没有折扣（率 100）不出角标', () => {
    expect(formatDiscount(10000, 10000, 100)).toBe('')
  })

  it('⚠️ 挂牌价比实付价还低时不出角标，不画一道「涨价」的线', () => {
    /*
     * 正常不会发生。但真发生了（配置写反、接口回了脏数据），
     * 划一道比现价还低的线等于告诉用户「这东西涨价了」——
     * 宁可什么都不显示。
     */
    expect(formatDiscount(10000, 8000, 88)).toBe('')
    expect(formatListPoints(10000, 8000)).toBe('')
  })

  it('划掉的挂牌价带千分位，和其他积分文案一个格式', () => {
    expect(formatListPoints(8800, 10000)).toBe('10,000 积分')
  })

  it('🔴 折扣率是 0 也不出角标 —— 0 的字面意思是白送，那一定是脏数据', () => {
    expect(formatDiscount(0, 10000, 0)).toBe('')
  })
})

/**
 * 「起」：各在售规格不同价时，对价后面必须加。
 *
 * 🔴 卡片显示的是**最便宜那个在售规格**的价。不加「起」的话，
 * 一个点进去选完规格发现要多付的用户会认为被骗了 ——
 * 而他没有任何办法从卡片上看出来这是最低价。
 */
describe('各规格不同价时的「起」', () => {
  it('纯积分商品：不同价加「起」，同价不加', () => {
    expect(formatCost(1, 8800, toMoney('0'), true)).toBe('8,800 积分 起')
    expect(formatCost(1, 8800, toMoney('0'), false)).toBe('8,800 积分')
  })

  it('积分+现金商品：「起」加在整串后面，不是夹在中间', () => {
    expect(formatCost(2, 8800, toMoney('5000.00'), true)).toBe('8,800 积分 + ¥5,000.00 起')
  })

  it('⚠️ 不传 varies 时维持原样 —— 旧调用点不会凭空多出一个「起」', () => {
    expect(formatCost(2, 8800, toMoney('5000.00'))).toBe('8,800 积分 + ¥5,000.00')
  })
})
