/**
 * 后端 JSON 契约的类型定义。
 *
 * 这个文件存在的唯一理由：solvela 后端的 JSON 有三处「长得像原始类型、但不能当原始类型用」的字段。
 * 用 branded type 把它们和普通 string 区分开，任何想当数字/日期直接用的地方都会编译报错。
 *
 * 三处分别是：
 *
 * 1. **ID 会变类型**。LongJsonSerializer 在 |value| <= 2^53-1 时输出 JSON 数字，
 *    超出时输出字符串。同一个字段的 JSON 类型取决于值的大小，等业务量上来才会暴露。
 *    对策：入口一律经 {@link toId} 归一成字符串，内部只认 {@link Id}。
 *
 * 2. **金额永远是字符串**。JsonConfig 给 BigDecimal 挂了 ToStringSerializer。
 *    对它做 `+`、`*`、`toFixed` 都是错的，必须走 utils/money 的 Decimal 封装。
 *
 * 3. **时间是没有时区的字符串**，形如 `2026-08-29 20:00:00`。
 *    既不能 `new Date(它)`（浏览器解析行为不一致），也不能拿本地时间去减它做倒计时。
 */

declare const brand: unique symbol

type Brand<T, B extends string> = T & { readonly [brand]: B }

/** 后端主键。**永远当字符串用**：不做算术、不和数字字面量比较、不 parseInt */
export type Id = Brand<string, 'Id'>

/** 金额。十进制字符串，运算一律走 utils/money */
export type Money = Brand<string, 'Money'>

/** 日期时间，格式 `yyyy-MM-dd HH:mm:ss`，**无时区信息** */
export type DateTimeString = Brand<string, 'DateTimeString'>

/** 日期，格式 `yyyy-MM-dd` */
export type DateString = Brand<string, 'DateString'>

/**
 * 后端<b>原始</b>响应的形状：`Id` 位置实际是 `string | number`。
 *
 * 🔴 这不是防御性编程，是契约本身：LongJsonSerializer 对 |v| ≤ 2^53-1 的 Long
 * 输出 JSON <b>数字</b>，超出才输出字符串。所以一个 `skuId` 在类型上是字符串、
 * 运行时却是 `6`——而 `6 === '6'` 恒 false。
 *
 * 2026-09-05 就是这么炸的：兑换页拿 URL query 里的 `sku`（永远是字符串）
 * 去和详情里的 `skuId`（数字）比，永远找不到，于是「请先回上一页选择规格」，
 * 而用户明明选了。TypeScript 一个字都没报，因为它以为两边都是 Id。
 *
 * 用法：给 fetch 的返回标 `Raw<T>`，再过一遍 normalize 把 id 收成字符串。
 * 嵌套的对象/数组字段这个映射管不到，要在 normalize 里手动递归。
 */
export type Raw<T> = {
  [K in keyof T]: T[K] extends Id
    ? string | number
    : T[K] extends Id | null
      ? string | number | null
      : T[K]
}

/**
 * 把后端下发的主键归一成 {@link Id}。
 *
 * 接受 number 是因为 LongJsonSerializer 对小值输出数字 —— 这是契约的一部分，不是脏数据。
 * 所有反序列化入口都必须过这一层，别在业务代码里直接 `as Id`。
 */
export function toId(raw: string | number): Id {
  if (typeof raw === 'number') {
    if (!Number.isSafeInteger(raw)) {
      // 走到这里说明这个数字在 JSON.parse 阶段就已经丢精度了，救不回来，只能让它响
      throw new RangeError(`主键 ${String(raw)} 超出 JS 安全整数范围，后端应以字符串下发`)
    }
    return String(raw) as Id
  }
  return raw as Id
}

/** 把后端下发的金额标成 {@link Money}。只做标记，不做运算 */
export function toMoney(raw: string): Money {
  return raw as Money
}

/** 把后端下发的日期时间标成 {@link DateTimeString} */
export function toDateTime(raw: string): DateTimeString {
  return raw as DateTimeString
}

/** 把后端下发的日期标成 {@link DateString} */
export function toDate(raw: string): DateString {
  return raw as DateString
}
