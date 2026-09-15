<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { type Address, fetchAddresses, formatAddressLine } from '@/api/address'
import { fetchAssets } from '@/api/assets'
import { type CouponTrialItem, trialCoupons } from '@/api/coupons'
import { ApiError } from '@/api/errors'
import { fetchCommodityDetail, OrderStatus, redeem, type RedeemResult } from '@/api/mall'
import { useAsync } from '@/composables/useAsync'
import { type Id, toId, type Money } from '@/types/contract'
import { formatCash, formatPoints } from '@/utils/cost'
import { compare, formatWithSeparator, money, mul, sub, ZERO } from '@/utils/money'

/**
 * 兑换确认页。<b>没有购物车</b>，一单一 SKU。
 *
 * <h3>为什么是独立一页，不是详情页上的底部弹层</h3>
 * 弹层要同时碰 teleport、滚动锁定和焦点陷阱 —— 项目原则里明写了那类别自己写。
 * 而且兑换会真的扣积分，值得一个「停下来看一眼」的确认步骤。
 *
 * <h3>SKU 与件数从 query 来，但<b>必须重新校验</b></h3>
 * 详情页把选好的 `sku` 与 `qty` 塞进 query，这样刷新和「去地址簿挑一个再回来」都不丢。
 * 但 query 是用户可以随手改的 —— 所以这里要拿商品详情把 SKU 对回去：
 * 认不出的 skuId、已无货的 SKU、超出库存或限兑的件数，一律拦下。
 * <b>服务端还要再校验一遍</b>，前端这道只是体验。
 *
 * <h3>积分够不够，前端算一遍只是为了话说得清楚</h3>
 * 真正的扣减与判断在服务端。这里算差额，是为了把按钮置灰的原因说成
 * 「还差 3,000 积分」而不是干置灰。
 *
 * <p>⚠️ 现金部分<b>没有支付链路</b>（后端一行支付代码都没有），
 * 所以 `payType=2` 的订单落 {@link OrderStatus.UNPAID}，如实显示「待支付」。
 */

const route = useRoute()
const router = useRouter()

const rawId = route.params.id
const commodityId = toId(Array.isArray(rawId) ? (rawId[0] ?? '') : (rawId ?? ''))

const detail = useAsync(() => fetchCommodityDetail(commodityId))
const assets = useAsync(fetchAssets)
const addresses = useAsync(fetchAddresses)

/* ---- SKU 与件数：从 query 读，拿详情校验 ---- */
function queryValue(key: string): string | null {
  const raw = route.query[key]
  const value = Array.isArray(raw) ? raw[0] : raw
  return typeof value === 'string' && value !== '' ? value : null
}

/** 认不出、已无货的 SKU 一律当成没选 */
const sku = computed(() => {
  const id = queryValue('sku')
  if (id === null) {
    return null
  }
  const found = (detail.data.value?.skus ?? []).find((s) => s.skuId === id)
  return found !== undefined && found.availableStock > 0 ? found : null
})

/** 件数被库存与限兑两头夹住。query 里写个 999 也只会按上限算 */
const quantity = computed(() => {
  const raw = Number.parseInt(queryValue('qty') ?? '1', 10)
  const wanted = Number.isFinite(raw) && raw > 0 ? raw : 1
  const stock = sku.value?.availableStock ?? 0
  const remaining = detail.data.value?.remainingCount
  const cap = remaining === null || remaining === undefined ? stock : Math.min(stock, remaining)
  return Math.max(1, Math.min(wanted, cap))
})

/** 规格摘要，如「颜色 午夜蓝 · 尺码 38」。无规格商品为空串 */
const skuText = computed(() =>
  Object.entries(sku.value?.skuAttrs ?? {})
    .map(([name, value]) => `${name} ${value}`)
    .join(' · '),
)

/* ---- 收货地址。只有实物要 ---- */
const needsAddress = computed(() => detail.data.value?.commodityType === 'PHYSICAL')

/** 用户在地址簿里挑过的那条（回来时带在 query 上） */
const pickedAddressId = computed<Id | null>(() => {
  const value = queryValue('address')
  return value === null ? null : toId(value)
})

/**
 * 最终用哪条地址：挑过就用挑的，否则用默认那条
 *（{@link fetchAddresses} 把默认地址排在最前，所以取第 0 条即可）。
 *
 * <h3>🔴 这里<b>不看商品类型</b>，是刻意的</h3>
 * 曾经这里判过 `if (!needsAddress) return null`，看着更「干净」，
 * 但它把整条链路的失败方向指错了：
 *
 * <ul>
 *   <li>给券<b>多带</b>一个地址 —— <b>完全无害</b>。后端只在实物分支读 addressId，
 *       券的订单 address_id 照样是 NULL（线上那一单验证过）；</li>
 *   <li>给实物<b>少带</b>一个地址 —— 直接兑不了，后端回「请选择收货地址」，
 *       而页面上地址那一栏明明显示着地址。</li>
 * </ul>
 *
 * 两种错法的代价差这么远，默认方向就该是「带上」。
 * commodityType 只要有任何一刻不是预期值（还没加载完、字段没到、类型改名），
 * 判在这里就会让实物兑换整个失效 —— 而那是用户唯一能看见的后果。
 *
 * <p>「要不要显示收货地址」是<b>展示</b>问题，交给模板里的 needsAddress。
 */
const address = computed<Address | null>(() => {
  const list = addresses.data.value ?? []
  if (pickedAddressId.value !== null) {
    /*
     * 🔴 两边都必须是字符串。后端的 Long 小值下发的是 JSON 数字，
     * 而 query 里永远是字符串 —— 归一在 api/address.ts 的反序列化边界做。
     * 少了那一步，`1 === '1'` 恒 false：挑过一次地址之后
     * 这里永远找不到，兑换页会一直提示「请选择收货地址」。
     */
    return list.find((a) => a.addressId === pickedAddressId.value) ?? null
  }
  return list[0] ?? null
})

function goPickAddress(): void {
  // 把当前 query 一起带过去，挑完原样带回来，SKU 与件数不丢
  void router.push({
    name: 'address-list',
    query: { ...route.query, pick: '1', commodity: commodityId },
  })
}

/* ---- 选券 ---- */
/*
 * 🔴 试算是【服务端】算的，这一页一个金额都不重算。
 *
 *    前端自己按券面规则算一遍看着很省一次请求，但那段逻辑（门槛、封顶、
 *    不能超过应付、向下取整）在服务端也有一份 —— 两份迟早不一致，
 *    而不一致的表现是「页面显示减 30，实际扣的时候只减了 20」。
 *
 * ⚠️ 试算结果不是承诺：从这里到点确认之间，券可能在另一个端上被用掉。
 *    真正作数的是下单时服务端重新试算并锁定的那一次，
 *    所以下单请求里只有「用哪张」，没有「减多少」。
 */
/*
 * 这里<b>没有</b>用 useAsync：那个composable 一构造就发请求，
 * 而 sku 要等商品详情回来才知道 —— 立刻发一次等于带着空 skuId 去问一遍。
 * 触发时机由下面那个 watch 管。
 */
const usableCoupons = ref<CouponTrialItem[]>([])
const unusableCoupons = ref<CouponTrialItem[]>([])
const trialLoading = ref(false)

/** 用户选中的券。null = 不使用优惠券，这是默认 */
const chosenCouponId = ref<Id | null>(null)
const couponPickerOpen = ref(false)

async function reloadTrial(skuId: Id): Promise<void> {
  trialLoading.value = true
  try {
    const outcome = await trialCoupons({ commodityId, skuId, quantity: quantity.value })
    usableCoupons.value = outcome.usable
    unusableCoupons.value = outcome.unusable
  } catch {
    /*
     * 选券挂了不该挡住兑换本身 —— 用户还是可以不用券把单下了。
     * 静默降级成「没有可用券」，而不是把整页变成错误态。
     */
    usableCoupons.value = []
    unusableCoupons.value = []
  } finally {
    trialLoading.value = false
  }
}

const chosenCoupon = computed<CouponTrialItem | null>(
  () => usableCoupons.value.find((item) => item.couponId === chosenCouponId.value) ?? null,
)

/**
 * 规格或件数一变就重新试算 —— 门槛是按<b>整单</b>判的。
 *
 * 🔴 顺带把已选的券清掉：换了规格之后，原来那张可能已经不满足门槛了。
 * 不清的话，用户会带着一张用不了的券去下单，然后被后端拒掉，
 * 而页面上那张券还好端端地显示着「可减 1000」。
 */
watch(
  [() => sku.value?.skuId, quantity],
  ([skuId]) => {
    chosenCouponId.value = null
    couponPickerOpen.value = false
    if (skuId !== undefined) {
      void reloadTrial(skuId)
    }
  },
  { immediate: true },
)

function pickCoupon(couponId: Id | null): void {
  chosenCouponId.value = couponId
  couponPickerOpen.value = false
}

/* ---- 账单 ---- */
/** 抵扣前的积分 = 单价 × 件数。积分是整数，直接乘 */
const originalPoints = computed(() => (sku.value?.pointsPrice ?? 0) * quantity.value)

/** 券减了多少积分。没选券就是 0 */
const couponDiscount = computed(() =>
  chosenCoupon.value === null ? 0 : Number(chosenCoupon.value.discountAmount),
)

/** 实付积分 = 抵扣前 - 券抵扣。服务端还会再算一遍，这里只为把话说清楚 */
const payPoints = computed(() => Math.max(0, originalPoints.value - couponDiscount.value))

/** 实付现金 = 单价 × 件数。**金额必须走 Decimal**，不许用 JS 原生乘 */
const payCash = computed<Money>(() =>
  sku.value === null ? ZERO : mul(money(sku.value.cashPrice), quantity.value),
)

const needsPayment = computed(() => detail.data.value?.payType === 2)

/** 积分余额。资产接口里 assetType 为 SCORE 的那一条 */
const pointsBalance = computed<Money>(() => {
  const found = (assets.data.value ?? []).find((a) => a.assetType === 'SCORE')
  return found === undefined ? ZERO : money(found.amount)
})

/** 还差多少积分。够了为 null */
const shortfall = computed<Money | null>(() => {
  const required = money(String(payPoints.value))
  return compare(pointsBalance.value, required) < 0 ? sub(required, pointsBalance.value) : null
})

/* ---- 提交 ---- */
const submitting = ref(false)
const hintText = ref('')
const result = ref<RedeemResult | null>(null)

/** 按钮为什么不能点，要说出来 —— 一个没有解释的置灰按钮等于死路 */
const blockedReason = computed<string | null>(() => {
  if (detail.data.value === null) {
    // 模板里整块都在 detail.data 非空之后才渲染，所以这条走不到；留着是为了
    // 让这个 computed 单独看也是自洽的
    return null
  }
  /*
   * 🔴 地址还在路上时也要拦。
   * 少了这一条，实物商品会在 addresses 落地前短暂放行 ——
   * 那一瞬间 address 是 null，请求就带着 null 出去了，
   * 后端回「请选择收货地址」，而页面上地址那一栏随后正常显示出来。
   */
  if (needsAddress.value && addresses.loading.value) {
    return '加载中…'
  }
  if (sku.value === null) {
    return '请先回上一页选择规格'
  }
  if (needsAddress.value && address.value === null) {
    return '请选择收货地址'
  }
  if (shortfall.value !== null) {
    return `积分不足，还差 ${formatWithSeparator(shortfall.value, 0)} 积分`
  }
  return null
})

async function onConfirm(): Promise<void> {
  const chosen = sku.value
  if (submitting.value || blockedReason.value !== null || chosen === null) {
    return
  }
  submitting.value = true
  hintText.value = ''
  try {
    /*
     * requestId 客户端生成，一次点击一个 —— 它只挡「连点两次提交」。
     * 🔴 它不是订单号：订单号由服务端生成，本身就是扣积分的幂等键
     *（落在 t_member_asset_transaction 的 UNIQUE(biz_ref_id, asset_type) 上）。
     */
    const outcome = await redeem({
      skuId: chosen.skuId,
      quantity: quantity.value,
      /*
       * 🔴 有地址就带上，<b>不判商品类型</b>。
       * 后端只在实物分支读它，给券多带一个是无害的；而少带一个的代价是
       * 实物压根兑不了（后端回「请选择收货地址」，页面上却显示着地址）。
       * 两种错法的代价差这么远，默认方向就该是「带上」。
       */
      addressId: address.value?.addressId ?? null,
      /*
       * 只报「用哪张」，不报「减多少」—— 后者由服务端重新试算。
       * 让客户端报数就是一个可以直接刷钱的口子。
       */
      couponId: chosenCouponId.value,
      requestId: crypto.randomUUID(),
    })
    result.value = outcome
    // 积分被扣了，余额得重新拉 —— 本地自己减一遍只是猜服务端扣了多少
    void assets.reload()
  } catch (error) {
    // 商品下架 / 无货 / 超限兑 / 积分不足 / 地址失效都会走到这里，都是预期内的
    hintText.value = error instanceof ApiError ? error.message : '兑换失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

function goRecords(): void {
  void router.replace({ name: 'mine' })
}
</script>

<template>
  <div class="page">
    <NavBar title="确认兑换" />

    <div v-if="detail.loading.value" class="loading" aria-hidden="true">
      <span class="loading__dot" />
    </div>

    <div v-else-if="detail.error.value !== null" class="loading loading--error" role="alert">
      <p class="loading__text">{{ detail.error.value }}</p>
      <Button variant="text" @click="detail.reload">重试</Button>
    </div>

    <template v-else-if="detail.data.value !== null">
      <!-- 兑换成功后整页换成结果，不再让用户看着一个还能点的按钮 -->
      <div v-if="result !== null" class="done">
        <span class="done__badge"><Icon name="check" :size="34" /></span>
        <p class="done__title">{{ result.message }}</p>
        <p class="done__no">订单号 {{ result.orderNo }}</p>
        <p v-if="result.status === OrderStatus.UNPAID" class="done__note">
          ⚠️ 现金部分还没有支付链路（后端未实现），这一单会停在「待支付」。
        </p>
        <!--
          只有实物才寄。券/红包画这一行等于告诉用户「你的券要走快递」。
          两个条件问的不是同一件事：needsAddress 是「这单要不要寄」，
          address 是「有没有地址」—— 都要成立才画得出这句话。
        -->
        <p
          v-else-if="result.status === OrderStatus.PENDING && needsAddress && address !== null"
          class="done__note"
        >
          商品将寄往：{{ formatAddressLine(address) }}
        </p>
        <p v-else-if="result.status === OrderStatus.PENDING" class="done__note">
          我们会尽快为你发放，可在「我的 → 兑换记录」查看
        </p>
        <Button @click="goRecords">查看我的记录</Button>
      </div>

      <template v-else>
        <!-- 兑的是什么 -->
        <div class="item">
          <span class="item__media" aria-hidden="true">
            {{ (detail.data.value.commodityName.split('·').pop() ?? '?').charAt(0) }}
          </span>
          <div class="item__body">
            <h2 class="item__title">{{ detail.data.value.commodityName }}</h2>
            <p v-if="skuText !== ''" class="item__spec">{{ skuText }}</p>
            <p class="item__qty">× {{ quantity }}</p>
          </div>
        </div>

        <!-- 寄到哪。虚拟商品没有这一段 -->
        <Section
          v-if="needsAddress"
          title="收货地址"
          :loading="addresses.loading.value"
          :error="addresses.error.value"
          :empty="false"
          empty-text=""
          @retry="addresses.reload"
        >
          <button v-if="address !== null" class="addr" type="button" @click="goPickAddress">
            <span class="addr__head">
              <span class="addr__receiver">{{ address.receiverName }}</span>
              <span class="addr__phone">{{ address.receiverPhone }}</span>
              <span v-if="address.isDefault" class="addr__default">默认</span>
            </span>
            <span class="addr__line">{{ formatAddressLine(address) }}</span>
            <Icon name="chevron" :size="18" class="addr__arrow" />
          </button>

          <!-- 一条地址都没有：不是空状态，是待办 -->
          <button v-else class="addr addr--empty" type="button" @click="goPickAddress">
            <span class="addr__line">还没有收货地址，去添加一个</span>
            <Icon name="chevron" :size="18" class="addr__arrow" />
          </button>
        </Section>

        <!--
          选券。
          🔴 用不了的券也列出来，置灰 + 一句原因 ——
             用户手里有券却在下单页看不到它，第一反应是系统坏了，
             而真实原因往往只是「没到门槛」，那一句话能省掉一次客服。
        -->
        <Section
          v-if="usableCoupons.length > 0 || unusableCoupons.length > 0"
          title="优惠券"
          :loading="trialLoading"
          :error="null"
          :empty="false"
          empty-text=""
        >
          <button type="button" class="coupon-row" @click="couponPickerOpen = !couponPickerOpen">
            <span v-if="chosenCoupon !== null" class="coupon-row__on">
              {{ chosenCoupon.couponName }} · 减 {{ chosenCoupon.discountAmount }}
            </span>
            <span v-else-if="usableCoupons.length > 0" class="coupon-row__hint">
              有 {{ usableCoupons.length }} 张可用
            </span>
            <span v-else class="coupon-row__muted">暂无可用券</span>
            <Icon name="chevron" :size="18" class="coupon-row__arrow" />
          </button>

          <ul v-if="couponPickerOpen" class="picks">
            <li>
              <button type="button" class="pick" @click="pickCoupon(null)">
                <span class="pick__name">不使用优惠券</span>
                <Icon v-if="chosenCouponId === null" name="check" :size="16" />
              </button>
            </li>
            <li v-for="item in usableCoupons" :key="item.couponId">
              <button type="button" class="pick" @click="pickCoupon(item.couponId)">
                <span class="pick__main">
                  <span class="pick__name">{{ item.couponName }}</span>
                  <span class="pick__save">可减 {{ item.discountAmount }} 积分</span>
                </span>
                <Icon v-if="chosenCouponId === item.couponId" name="check" :size="16" />
              </button>
            </li>
            <!-- 不可用的：点不动，但看得见，而且说得出为什么 -->
            <li v-for="item in unusableCoupons" :key="item.couponId">
              <span class="pick pick--off">
                <span class="pick__main">
                  <span class="pick__name">{{ item.couponName }}</span>
                  <span class="pick__why">{{ item.reasonDesc }}</span>
                </span>
              </span>
            </li>
          </ul>
        </Section>

        <!-- 付出什么 -->
        <div class="bill">
          <div class="bill__row">
            <span>消耗积分</span>
            <span class="bill__value">{{ formatPoints(payPoints) }}</span>
          </div>
          <!--
            抵扣单独一行，而不是把「消耗积分」直接改小。
            直接改小的话用户看不出券生效了没有 —— 而那正是他点确认前要确认的事。
          -->
          <div v-if="couponDiscount > 0" class="bill__row bill__row--sub">
            <span>券抵扣</span>
            <span class="bill__value bill__value--cut">
              -{{ formatPoints(couponDiscount) }}（原价 {{ formatPoints(originalPoints) }}）
            </span>
          </div>
          <div class="bill__row bill__row--sub">
            <span>当前余额</span>
            <span class="bill__value">{{ formatWithSeparator(pointsBalance, 0) }} 积分</span>
          </div>
          <div v-if="needsPayment" class="bill__row">
            <span>需支付现金</span>
            <span class="bill__value">{{ formatCash(payCash) }}</span>
          </div>
        </div>

        <p v-if="detail.data.value.exchangeNotice !== null" class="notice">
          {{ detail.data.value.exchangeNotice }}
        </p>

        <div class="bar">
          <p v-if="hintText !== ''" class="bar__hint bar__hint--error" role="alert">
            {{ hintText }}
          </p>
          <p v-else-if="blockedReason !== null" class="bar__hint">{{ blockedReason }}</p>
          <Button :disabled="blockedReason !== null" :loading="submitting" @click="onConfirm">
            确认兑换
          </Button>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.coupon-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-md);
  border: 0;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-surface);
  font-size: var(--sv-font-caption);
  text-align: left;
  cursor: pointer;
}

.coupon-row__on {
  color: var(--sv-color-primary);
  font-weight: 500;
}

.coupon-row__hint {
  color: var(--sv-text-primary);
}

.coupon-row__muted {
  color: var(--sv-text-placeholder);
}

.coupon-row__arrow {
  flex: none;
  color: var(--sv-text-placeholder);
}

.picks {
  margin: var(--sv-space-xs) 0 0;
  padding: 0;
  list-style: none;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-surface);
  overflow: hidden;
}

.pick {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-md);
  border: 0;
  border-top: 1px solid var(--sv-border-color);
  background: none;
  font-size: var(--sv-font-caption);
  text-align: left;
  color: var(--sv-text-primary);
  cursor: pointer;
}

.picks > li:first-child .pick {
  border-top: 0;
}

/* 不可用的券压暗且不可点，但【留在列表里】—— 见模板上那段红字 */
.pick--off {
  opacity: 0.55;
  cursor: default;
}

.pick__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.pick__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pick__save {
  font-size: var(--sv-font-footnote);
  color: var(--sv-color-primary);
}

.pick__why {
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
}

.bill__value--cut {
  color: var(--sv-color-primary);
}

.page {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding-bottom: calc(112px + var(--sv-safe-bottom));
}

.loading {
  display: flex;
  justify-content: center;
  padding-top: 30vh;
}

.loading--error {
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-md);
  padding: 30vh var(--sv-space-page) 0;
}

.loading__text {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.loading__dot {
  width: 28px;
  height: 28px;
  border: 3px solid var(--sv-bg-fill);
  border-top-color: var(--sv-color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .loading__dot {
    animation-duration: 2.4s;
  }
}

/* ---- 商品 ---- */
.item {
  display: flex;
  gap: var(--sv-space-md);
  margin: 0 var(--sv-space-page);
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.item__media {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-fill);
  color: var(--sv-text-placeholder);
  font-size: 28px;
  font-weight: 700;
}

.item__body {
  flex: 1;
  min-width: 0;
}

.item__title {
  margin: 0;
  font-size: var(--sv-font-caption);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__spec {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
}

.item__qty {
  margin: var(--sv-space-sm) 0 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}

/* ---- 地址 ---- */
.addr {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-xs);
  width: 100%;
  border: 0;
  padding: var(--sv-space-md) calc(var(--sv-space-md) + 22px) var(--sv-space-md) var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.addr:active {
  background: var(--sv-bg-pressed);
}

.addr:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: -2px;
}

.addr__head {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
}

.addr__receiver {
  font-size: var(--sv-font-body);
  font-weight: 600;
}

.addr__phone {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  font-variant-numeric: tabular-nums;
}

.addr__default {
  padding: 1px var(--sv-space-sm);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-size: var(--sv-font-footnote);
}

.addr__line {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.5;
}

.addr--empty .addr__line {
  color: var(--sv-color-primary);
}

.addr__arrow {
  position: absolute;
  top: 50%;
  right: var(--sv-space-md);
  transform: translateY(-50%);
  color: var(--sv-text-placeholder);
}

/* ---- 账单 ---- */
.bill {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin: 0 var(--sv-space-page);
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  font-size: var(--sv-font-caption);
}

.bill__row {
  display: flex;
  justify-content: space-between;
  gap: var(--sv-space-md);
}

.bill__row--sub {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}

.bill__value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.bill__row--sub .bill__value {
  font-weight: 400;
}

.notice {
  margin: 0 var(--sv-space-page);
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
}

/* ---- 底部操作条 ---- */
.bar {
  position: fixed;
  inset: auto 0 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-xs);
  padding: var(--sv-space-md) var(--sv-space-page) calc(var(--sv-space-md) + var(--sv-safe-bottom));
  background: var(--sv-bg-surface);
  box-shadow: 0 -0.5px 0 var(--sv-border-color);
}

.bar__hint {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  text-align: center;
}

.bar__hint--error {
  color: var(--sv-color-danger);
}

/* ---- 兑换结果 ---- */
.done {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-md);
  padding: 12vh var(--sv-space-page) 0;
  text-align: center;
}

.done__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.done__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  font-weight: 700;
}

.done__no {
  margin: 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}

.done__note {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.6;
}
</style>
