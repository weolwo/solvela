import type { Address } from '@/api/address'
import type { CommodityBrief, CommodityDetail, CommoditySku, MallCategory } from '@/api/mall'
import { toId, toMoney } from '@/types/contract'

/**
 * 测试用的样例数据。
 *
 * <h3>为什么集中一份</h3>
 * 2026-09-05 商城读路径接通真实接口之后，`api/mall.ts` 里那份桩数据没了，
 * 而五个 spec 都需要一组「长得像真实响应」的数据。各写各的话，
 * 「午夜蓝 38 库存是 8 还是 6」这种细节会在文件之间漂，
 * 而断言里写着具体数字的用例会因为改了别处的 fixture 莫名其妙地红。
 *
 * <p>⚠️ 这里的数据只服务于测试，<b>不是</b>桩：它不会被打进任何一次构建
 *（只有 spec 会 import 它），也不该被拿去当「后端应该返回什么」的依据 ——
 * 那个依据是 `数据库SQL脚本/mall.sql`。
 */

export const CATEGORIES: MallCategory[] = [
  { id: toId('1'), parentId: toId('0'), categoryName: '数码3C', iconUrl: null, sort: 1 },
  { id: toId('2'), parentId: toId('0'), categoryName: '服饰', iconUrl: null, sort: 2 },
  { id: toId('5'), parentId: toId('0'), categoryName: '虚拟权益', iconUrl: null, sort: 5 },
]

function brief(
  id: string,
  name: string,
  categoryId: string,
  type: CommodityBrief['commodityType'],
  payType: 1 | 2,
  points: number,
  cash: string,
  original: string,
  stock: number,
  favorite = false,
): CommodityBrief {
  return {
    commodityId: toId(id),
    commodityCode: `DEMO${id}AAAA`,
    categoryId: toId(categoryId),
    commodityType: type,
    commodityName: name,
    commodityIntro: null,
    coverUrl: null,
    payType,
    pointsPrice: points,
    cashPrice: toMoney(cash),
    originalPrice: toMoney(original),
    favorite,
    availableStock: stock,
  }
}

/** 7002 是「积分 + 现金」那件，用来验两种对价都写得出来 */
export const COMMODITIES: CommodityBrief[] = [
  // 🔴 7001 刻意有图、7002 刻意没图：渲染 <img> 和退回首字占位是两条路，
  //    都要有断言盯着。这个功能上线前就是「占位块从没变成图」，没人发现
  {
    ...brief('7001', '样例·Redmi Note 4', '1', 'PHYSICAL', 1, 45000, '0', '1299.00', 12),
    coverUrl: 'http://127.0.0.1:1024/support/file/public/mall_commodity/demo.png',
  },
  brief(
    '7002',
    '样例·Apple Watch Series 6',
    '1',
    'PHYSICAL',
    2,
    45000,
    '299.00',
    '3199.00',
    24,
    true,
  ),
  brief('7005', '样例·纯棉圆领 T 恤', '2', 'PHYSICAL', 1, 8900, '0', '129.00', 88),
  brief('7008', '样例·10 元话费券', '5', 'COUPON', 1, 1000, '0', '10.00', 999),
]

function sku(
  id: string,
  attrs: Record<string, string>,
  points: number,
  cash: string,
  stock: number,
): CommoditySku {
  return {
    skuId: toId(id),
    skuCode: `DEMOSKU${id}`,
    skuAttrs: attrs,
    skuCoverUrl: null,
    pointsPrice: points,
    cashPrice: toMoney(cash),
    availableStock: stock,
  }
}

/**
 * 7002 的规格：两组、六个组合，其中珍珠白两个都无货、曜石黑 42 无货。
 * 这三个 0 是为了验「可选性跟着已选规格收窄」那条。
 */
const SKUS_7002: CommoditySku[] = [
  sku('80021', { 颜色: '午夜蓝', 尺码: '38' }, 45000, '299.00', 8),
  sku('80022', { 颜色: '午夜蓝', 尺码: '42' }, 45000, '299.00', 6),
  sku('80023', { 颜色: '曜石黑', 尺码: '38' }, 45000, '299.00', 10),
  sku('80024', { 颜色: '曜石黑', 尺码: '42' }, 45000, '299.00', 0),
  sku('80025', { 颜色: '珍珠白', 尺码: '38' }, 47000, '299.00', 0),
  sku('80026', { 颜色: '珍珠白', 尺码: '42' }, 47000, '299.00', 0),
]

const SKUS: Record<string, CommoditySku[]> = { '7002': SKUS_7002 }

export function detailOf(commodityId: string): CommodityDetail {
  const base = COMMODITIES.find((c) => c.commodityId === commodityId) ?? COMMODITIES[0]!
  // 无规格商品也有一行 SKU，skuAttrs 为空对象 —— 对齐 DDL
  const skus = SKUS[base.commodityId] ?? [
    sku(`9${base.commodityId}`, {}, base.pointsPrice, base.cashPrice, base.availableStock),
  ]
  return {
    ...base,
    detailContent: '<p>样例图文详情。</p>',
    exchangeNotice:
      base.commodityType === 'PHYSICAL'
        ? '兑换成功后 15 个工作日内寄出，节假日顺延。'
        : '兑换后权益实时到账，有效期 90 天。',
    bannerUrls: [],
    limitPeriod: 'DAILY',
    limitCount: 2,
    remainingCount: 2,
    skus,
  }
}

export const ADDRESSES: Address[] = [
  {
    id: toId('8001'),
    receiverName: '张三',
    // 脱敏值：列表接口下发的就是这个，不是明文
    receiverPhone: '138****8000',
    province: '广东省',
    city: '深圳市',
    district: '南山区',
    detailAddress: '科技园南路 XX 号 XX 大厦 1801',
    isDefault: true,
  },
  {
    id: toId('8002'),
    receiverName: '李四',
    receiverPhone: '139****9111',
    province: '浙江省',
    city: '杭州市',
    district: '西湖区',
    detailAddress: '文三路 XX 号 XX 大厦 502',
    isDefault: false,
  },
]

/**
 * 🔴 状态写字面量 30 而不是 `OrderStatus.FINISHED`。
 *
 * 本文件被 `vi.mock('@/api/mall')` 的工厂 await，如果它反过来<b>运行时</b>
 * 依赖 `@/api/mall`，两边就会互相等 —— 表现是 vitest 挂住、一个字都不输出，
 * 既不报错也不超时（实测踩过）。所以对 mall 只能是 `import type`。
 *
 * 30 是 `MallOrderStatusEnum.FINISHED` 的值，那个数值本身就是契约。
 */
export const REDEEM_RESULT = {
  orderNo: 'DEMO202609050001',
  status: 30 as const,
  message: '兑换成功，权益已到账',
}
