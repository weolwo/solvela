import type { Money } from '@/types/contract'
import { formatWithSeparator, isZero, money } from '@/utils/money'

/**
 * 商城对价的展示格式化。
 *
 * <h3>为什么在 utils 而不是页面里</h3>
 * 商品卡、详情页、兑换页、收藏页四处都要把同一份对价写成一行字。
 * 各写一遍的话，改个「积分」的写法要改四处，而漏改的表现是同一件商品
 * 在列表和详情里价格看着不一样 —— 那是最容易被当成 bug 上报的一类问题。
 *
 * <h3>🔴 积分是整数，现金才是小数</h3>
 * `t_mall_commodity.points_price` 是 `int`，`cash_price` 是 `decimal(10,2)`。
 * 两者不是一类东西，所以这里是两个函数、两套参数类型：
 * 积分收 `number`，现金收 {@link Money}（十进制字符串，必须走 Decimal 封装）。
 *
 * <h3>🔴 两个划线位，不要混</h3>
 * `originalPrice`（{@link formatWorth}）是「值多少钱」—— `价值 ¥1,999`，现金；
 * `listPointsPrice`（{@link formatListPoints}）是「原本要多少分」—— 等级折扣前的挂牌价。
 * 一件商品可能两个都有，但卡片上只放得下一个，取舍见 `ProductCard.vue`。
 *
 * <h3>🔴 划线价是「值多少钱」，不是「原来要多少积分」</h3>
 * `original_price` 的列注释原文：「仅前端展示『价值￥199』，纯积分商品可留 0」。
 * 所以它走 {@link formatWorth} 输出 `价值 ¥1,999`，<b>不是</b>把积分划一道。
 * 第一版前端把它当成积分原价，一件 45,000 积分的表划线位显示 `65,000 积分` —— 那是错的。
 */

/** 现金要带分：¥299 和 ¥299.50 是两个数 */
const CASH_DECIMALS = 2

/** 积分：`45,000 积分` */
export function formatPoints(points: number): string {
  return `${points.toLocaleString('en-US')} 积分`
}

/** 现金：`¥299.00` */
export function formatCash(cash: Money): string {
  return `¥${formatWithSeparator(cash, CASH_DECIMALS)}`
}

/**
 * 完整对价。两种形态，对齐 `MallPayTypeEnum`：
 * `45,000 积分`（payType=1） / `45,000 积分 + ¥299.00`（payType=2）。
 *
 * <p>刻意按 `payType` 分支而不是「cash 不为 0 就拼上」：
 * 后者在运营把现金价配成 0 的 payType=2 商品上会静默少显示一半信息。
 *
 * <h3>🔴 `varies` 是「各规格不同价」，必须加「起」</h3>
 * 列表与详情顶部显示的是**最便宜那个在售规格**的价。不加「起」的话，
 * 一个点进去选完规格发现要多付的用户会认为被骗了 ——
 * 而他没有任何办法从卡片上看出来这是最低价。
 */
export function formatCost(payType: 1 | 2, points: number, cash: Money, varies = false): string {
  const cost = payType === 2 ? `${formatPoints(points)} + ${formatCash(cash)}` : formatPoints(points)
  return varies ? `${cost} 起` : cost
}

/**
 * 等级折扣的文案：`8.8折` / `9折`。
 *
 * 🔴 **判据是两个价不相等，不是 `gradeDiscountPercent < 100`。**
 * 商品可以单独退出等级折扣（`grade_price_flag=0`，成本价/秒杀品），
 * 那时这个人的折扣率照样是 88，但这件商品就是原价 ——
 * 按折扣率判会在一件没便宜的商品上挂出「8.8折」，用户点进去发现没优惠。
 *
 * ⚠️ 这里只管**写字**，不管算价。折后价是服务端给的 `pointsPrice`，
 * 端上任何一次自己乘折扣率都会因为取整方向对不上实际扣的分。
 *
 * @returns 没享到折扣时返回空串 —— 调用方据此不渲染
 */
export function formatDiscount(
  pointsPrice: number,
  listPointsPrice: number,
  percent: number,
): string {
  if (listPointsPrice <= pointsPrice || percent >= 100 || percent <= 0) {
    return ''
  }
  // 88 → 8.8折，90 → 9折（不写成 9.0折）
  const tenths = percent / 10
  return `${Number.isInteger(tenths) ? tenths : tenths.toFixed(1)}折`
}

/** 划掉的挂牌积分价：`10,000 积分`。没享到折扣时返回空串 */
export function formatListPoints(pointsPrice: number, listPointsPrice: number): string {
  return listPointsPrice > pointsPrice ? formatPoints(listPointsPrice) : ''
}

/**
 * 划线原价：`价值 ¥1,999`。为 0 表示运营没配，返回空串 —— 调用方据此不渲染那一行。
 */
export function formatWorth(originalPrice: Money): string {
  return isZero(money(originalPrice)) ? '' : `价值 ${formatCash(originalPrice)}`
}
