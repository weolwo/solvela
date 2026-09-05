import type { Id, Money } from '@/types/contract'

import { request } from './http'

/**
 * 积分商城：分类、商品、SKU 与兑换。
 *
 * <h3>🔴 本文件的字段名一律照 `数据库SQL脚本/mall.sql`，不另发明</h3>
 * 七张表（分类/商品/SKU/订单/限兑/地址/收藏）与域层<b>后端都已经建好</b>，
 * 缺的只是 C 端那一半接口。所以这里的形状不是「前端提的建议」，
 * 而是<b>照着表长的</b> —— 后端补上 `MallApi` 之后两边直接对得上，不用再翻译一遍。
 *
 * <p>字段命名保持后端口径（`commodityName` 而不是 `title`）：多一层改名就多一处
 * 「这两个是不是同一个东西」的疑问，而它挡不住任何 bug。
 * 路由与目录仍用 product（`/product/:id`）—— 那是给用户看的地址，不是契约。
 *
 * <h3>这是「兑换」，不是「购物」</h3>
 * <b>没有购物车</b>：一单一 SKU。DDL 开头那段明写了刻意不做的东西 ——
 * 购物车、多商品合单、优惠券叠加、满减、运费模板、会员等级差异化定价。
 * 想加之前先回去看那一段。
 *
 * <h3>只有两种对价</h3>
 * `MallPayTypeEnum` 只有 `POINTS(1)` 与 `POINTS_CASH(2)`，<b>没有纯现金商品</b>。
 * 所以别写「纯现金」那条分支 —— 少一条分支就少一处将来会漂的代码。
 *
 * <h3>🔴 积分是整数，现金才是小数</h3>
 * `points_price int` vs `cash_price decimal(10,2)`。两者不是一类东西：
 * 积分用 `number`，现金用 {@link Money}（十进制字符串，运算走 utils/money）。
 * 混为一谈的代价是「45000.00 积分」这种展示，或者更糟 —— 拿 Decimal 去算一个整数。
 *
 * <h3>为什么没有图</h3>
 * 网关还没暴露文件下载接口。商品只给 `coverFileId`，页面拿不到就画占位块。
 * 分类图标同理（`iconFileId`）—— <b>不是前端映射的图标名</b>，是运营配的一张图。
 */

/* ------------------------------------------------------------------ *
 * 分类
 * ------------------------------------------------------------------ */

/**
 * 商品分类。
 *
 * <p>⚠️ <b>没有 categoryCode</b>，只有自增 `id`。DDL 注释解释过：分类是纯运营数据，
 * 代码不引用它（「一律按 code 引用」那条教训针对的是被代码硬编码引用的分类）。
 * <p>业务上限死两级，`parentId=0` 为顶级。
 */
export interface MallCategory {
  id: Id
  parentId: Id
  categoryName: string
  /** 分类图标的 file_id。网关还没暴露文件下载，现在恒为 null */
  iconFileId: string | null
  sort: number
}

/* ------------------------------------------------------------------ *
 * 商品
 * ------------------------------------------------------------------ */

/** 商品性质，决定走哪条履约通道。取值对齐后端 `PrizeTypeEnum` */
export type CommodityType =
  /** 实物，要寄 —— <b>兑换时必须选收货地址</b> */
  | 'PHYSICAL'
  /** 优惠券，落 t_member_coupon */
  | 'COUPON'
  /** 现金/红包，走钱包入账 */
  | 'BALANCE'

/** 支付方式。对齐 `MallPayTypeEnum`，只有这两种 */
export type PayType =
  /** 纯积分 */
  | 1
  /** 积分 + 现金 */
  | 2

/** 商品列表项。对应后端的 `MallCommodityBriefView` */
export interface CommodityBrief {
  commodityId: Id
  /** 对外唯一标识，跨环境稳定。列表里用不到，但下单链路按它引用 */
  commodityCode: string
  categoryId: Id
  commodityType: CommodityType
  commodityName: string
  /** 副标题/一句话卖点 */
  commodityIntro: string | null
  /** 封面图 file_id。现在恒为 null，页面画占位块 */
  coverFileId: string | null
  payType: PayType
  /** 兑换所需积分。**整数** */
  pointsPrice: number
  /** 兑换所需现金。`payType=1` 时恒为 '0' */
  cashPrice: Money
  /**
   * 划线原价。
   *
   * 🔴 <b>这是「值多少钱」，不是「原来要多少积分」。</b>
   * DDL 列注释原文：「仅前端展示『价值￥199』，纯积分商品可留 0」。
   * 所以划线位显示的是 `价值 ¥1,999`，<b>不是</b> `65,000 积分`。为 '0' 时不展示。
   */
  originalPrice: Money
  /** 当前会员有没有收藏。未登录时后端回 false */
  favorite: boolean
  /** 各 SKU 可用库存之和。0 表示整个商品无货 */
  availableStock: number
}

/** 一个 SKU。对应 `t_mall_sku` */
export interface CommoditySku {
  skuId: Id
  skuCode: string
  /**
   * 规格组合，如 `{ 颜色: '星空灰', 尺码: 'XL' }`。
   *
   * <p>⚠️ <b>无规格商品也有一行 SKU，`skuAttrs` 是空对象</b>（DDL 明写）。
   * 所以「有没有规格可选」要看这个对象空不空，不要看 SKU 列表长不长。
   *
   * <p>规格<b>分组</b>（有哪几组、每组有哪些值）不由接口给 —— 表里就只有这份 JSON，
   * 由 {@link groupSkuAttributes} 从 SKU 列表推出来。
   */
  skuAttrs: Record<string, string>
  /** 该规格专属图。为空则用商品封面 */
  skuCoverFileId: string | null
  /** 本规格所需积分。后端已把「继承商品基准价」算好，这里一定有值 */
  pointsPrice: number
  cashPrice: Money
  /** `total_stock - locked_stock - sold_count`，DDL 里是虚拟列 */
  availableStock: number
}

/** 限兑周期。取值对齐 `t_promotion_config.limit_period` 的字典 */
export type LimitPeriod = 'LIFETIME' | 'DAILY' | 'WEEKLY' | 'MONTHLY'

/** 商品详情。对应 `MallCommodityDetailView` */
export interface CommodityDetail extends CommodityBrief {
  /** 图文详情，富文本 HTML。来自运营后台，按可信内容渲染 */
  detailContent: string | null
  /** 兑换须知：券的核销说明、实物的发货时效等。C 端下单页固定展示 */
  exchangeNotice: string | null
  /** 轮播图 file_id 列表。来自 t_file_relation，现在恒为空数组 */
  bannerFileIds: string[]
  limitPeriod: LimitPeriod
  /** 周期内单会员限兑件数。0 = 不限制 */
  limitCount: number
  /**
   * 本周期<b>还能兑几件</b>。由后端用数据库时钟算好下发 ——
   * 前端拿 limitCount 减自己数的次数会和服务端的 period_key 口径对不上。
   * `limitCount=0`（不限）时为 null。
   */
  remainingCount: number | null
  /** 至少一行。无规格商品也有一行，见 {@link CommoditySku.skuAttrs} */
  skus: CommoditySku[]
}

/**
 * 把 SKU 列表推成「规格分组」：`[{ name: '颜色', values: ['星空灰', ...] }, ...]`。
 *
 * <p>表里只有每个 SKU 的 `skuAttrs` JSON，没有单独的规格表 —— 分组是算出来的。
 * 放在这里而不是页面里：详情页和兑换页都要用同一份分组结果。
 *
 * <p>组的顺序按<b>第一个 SKU 的键顺序</b>（JSON 保序），值的顺序按首次出现。
 * 这样运营在后台怎么排的，C 端就怎么显示。
 */
export function groupSkuAttributes(skus: CommoditySku[]): { name: string; values: string[] }[] {
  const groups = new Map<string, string[]>()
  for (const sku of skus) {
    for (const [name, value] of Object.entries(sku.skuAttrs)) {
      const values = groups.get(name)
      if (values === undefined) {
        groups.set(name, [value])
      } else if (!values.includes(value)) {
        values.push(value)
      }
    }
  }
  return [...groups].map(([name, values]) => ({ name, values }))
}

/** 按选中的规格找 SKU。没选全或找不到都返回 null */
export function findSku(
  skus: CommoditySku[],
  selections: Record<string, string>,
  groupCount: number,
): CommoditySku | null {
  if (Object.keys(selections).length < groupCount) {
    return null
  }
  return (
    skus.find((sku) =>
      Object.entries(selections).every(([name, value]) => sku.skuAttrs[name] === value),
    ) ?? null
  )
}

/**
 * 某个规格值还有没有货：只要<b>存在一个</b>包含它、且与当前其它选择兼容、且有库存的 SKU。
 *
 * <p>「与当前其它选择兼容」这一条是必要的：选了「珍珠白」之后，
 * 只有白色那几个 SKU 里有货的尺码才该是可选的 —— 否则用户选完两项才被告知没货。
 */
export function isOptionAvailable(
  skus: CommoditySku[],
  selections: Record<string, string>,
  name: string,
  value: string,
): boolean {
  return skus.some((sku) => {
    if (sku.skuAttrs[name] !== value || sku.availableStock <= 0) {
      return false
    }
    return Object.entries(selections).every(([n, v]) => n === name || sku.skuAttrs[n] === v)
  })
}

/* ------------------------------------------------------------------ *
 * 兑换订单
 * ------------------------------------------------------------------ */

/**
 * 订单状态。对齐后端 `MallOrderStatusEnum`，<b>数值就是契约</b>。
 *
 * <p>状态机：`0 →(支付/直接扣分)→ 10 →(投递履约)→ 20 →(履约回执)→ 30 / 60`，
 * 待支付超时或用户取消走 40（释放库存 + 原路退积分）。
 */
export const OrderStatus = {
  UNPAID: 0,
  PENDING: 10,
  FULFILLING: 20,
  FINISHED: 30,
  CANCELLED: 40,
  REFUNDED: 50,
  FAILED: 60,
} as const

export type OrderStatusValue = (typeof OrderStatus)[keyof typeof OrderStatus]

/** 兑换没被受理的原因。域只给 reason，说什么由网关决定 */
export type RedeemRejectReason =
  | 'COMMODITY_OFF'
  | 'OUT_OF_STOCK'
  | 'EXCHANGE_LIMITED'
  | 'POINTS_NOT_ENOUGH'
  | 'ADDRESS_REQUIRED'
  | 'ADDRESS_NOT_FOUND'

export interface RedeemRequest {
  skuId: Id
  /** 兑换件数，至少 1 */
  quantity: number
  /** 收货地址 id。**PHYSICAL 必填**，其余传 null */
  addressId: Id | null
  /**
   * 幂等键，<b>客户端生成，一次点击一个</b>。
   *
   * ⚠️ 它<b>不是订单号</b>。订单号由服务端生成，本身就是扣积分的幂等键
   *（落在 `t_member_asset_transaction` 的 `UNIQUE(biz_ref_id, asset_type)` 上，
   * DDL 明写「重复扣款天然幂等，不要另造去重表」）。
   * 这里这个只挡「用户连点两次提交」，挡在网关那一层，和抽奖同一个做法。
   */
  requestId: string
}

export interface RedeemResult {
  /** 服务端生成的订单号 */
  orderNo: string
  status: OrderStatusValue
  /** 给用户看的一句话，由网关那一层决定措辞（域只陈述发生了什么） */
  message: string
}

/* ------------------------------------------------------------------ *
 * 下面到文件结尾都是桩。后端补上 MallApi 后按注释替换函数体
 * ------------------------------------------------------------------ */

/* ------------------------------------------------------------------ *
 * 读路径已接通真实接口
 * ------------------------------------------------------------------ */

/** 商品分类。只出启用中的，按运营排的顺序。匿名可访问 */
export function fetchCategories(): Promise<MallCategory[]> {
  return request<MallCategory[]>({ url: '/mall/category' })
}

/** 商品列表的查询条件。字段对齐后端 `MallCommodityPageCmd` 与那条组合索引 */
export interface CommodityQuery {
  categoryId?: Id | null
  keyword?: string
  /** SORT(默认，运营权重) / SOLD(热销) / POINTS_ASC(积分从低到高) */
  sortBy?: 'SORT' | 'SOLD' | 'POINTS_ASC'
  pageNum?: number
  pageSize?: number
}

export interface CommodityPage {
  list: CommodityBrief[]
  /** 总条数。用来显示「共 N 件」，也用来判断还有没有下一页 */
  total: number
}

/**
 * 商品列表。只出上架中且在上架有效期内的。匿名可访问（未登录时 favorite 恒 false）。
 *
 * <p>🔴 <b>筛选与排序都在服务端做</b>。第一版是本地过滤，那在有分页之后
 * 立刻就是错的（只能搜到当前页）—— 所以关键词与分类要作为参数传下去，
 * 不要拿回来自己 filter。
 */
export function fetchCommodities(query: CommodityQuery = {}): Promise<CommodityPage> {
  return request<CommodityPage>({ url: '/mall/commodity', params: query })
}

/** 商品详情。不存在或已下架 → 后端回 404，这里抛 ApiError */
export function fetchCommodityDetail(commodityId: Id): Promise<CommodityDetail> {
  return request<CommodityDetail>({ url: `/mall/commodity/${commodityId}` })
}

/**
 * 我收藏的商品，按收藏时间倒序。
 *
 * <p>⚠️ <b>已下架的不出现</b>：收藏行还在库里（用户可能还想着它会回来），
 * 但列表不展示一个点不进去的卡片。
 */
export function fetchFavorites(): Promise<CommodityBrief[]> {
  return request<CommodityBrief[]>({ url: '/mall/favorite' })
}

/**
 * 收藏 / 取消收藏。<b>商品粒度，不是 SKU 粒度</b> ——
 * 用户收藏的是「那件 T 恤」，不是「星空黑 XL」。
 *
 * <p>两个方向都幂等：重复收藏靠唯一键挡住，取消一条已经不在的也不报错
 *（前端连点两次是常态）。取消是<b>物理删</b> ——
 * 软删会在「收藏 → 取消 → 再收藏」时撞唯一键，而收藏没有历史价值。
 */
export function toggleFavorite(commodityId: Id, favorite: boolean): Promise<void> {
  return request<void>({
    url: `/mall/favorite/${commodityId}`,
    method: favorite ? 'PUT' : 'DELETE',
  })
}

/**
 * 兑换。2026-09-05 已接通真实接口。
 *
 * <p>服务端在<b>一个事务</b>里做完：锁库存 → 扣限兑 → 扣积分 → 落订单。
 * 前端只调一次、只看结果 —— 「先调 A 再调 B」的写法中间断网就是账不平。
 *
 * <p>🔴 <b>订单号由服务端生成</b>，它本身就是扣积分的幂等键。
 * 这里的 `requestId` 是另一件事：只挡「用户连点两次提交」。
 *
 * <p>库存没了、超限兑、积分不足都会回 4xx 并带一句人话，这里抛 ApiError ——
 * <b>那几种都是预期内的</b>，按 message 提示即可。
 *
 * <p>⚠️ 现金部分<b>没有支付链路</b>，所以 payType=2 的商品兑换后落
 * {@link OrderStatus.UNPAID}，页面如实显示「待支付」而不是假装成了。
 */
export function redeem(payload: RedeemRequest): Promise<RedeemResult> {
  return request<RedeemResult>({ url: '/mall/redeem', method: 'POST', data: payload })
}
