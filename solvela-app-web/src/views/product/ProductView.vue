<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchCommodityDetail, findSku, groupSkuAttributes, isOptionAvailable } from '@/api/mall'
import { useAsync } from '@/composables/useAsync'
import { useFavorites } from '@/composables/useFavorites'
import { useAuthStore } from '@/stores/auth'
import { toId } from '@/types/contract'
import { formatCost, formatWorth } from '@/utils/cost'

/**
 * 商品详情页。
 *
 * <h3>为什么匿名可访问</h3>
 * 路由标了 `anonymous` —— 和活动专题页同一个理由：商品链接是要被分享出去的，
 * 要求先登录才能看一眼等于把分享链路掐断。<b>兑换那一步再要求登录</b>。
 *
 * <h3>规格是从 SKU 列表<b>推</b>出来的，不是接口给的</h3>
 * `t_mall_sku` 里只有每个 SKU 的 `sku_attrs` JSON（`{颜色:"午夜蓝", 尺码:"38"}`），
 * 没有单独的规格表。所以「有哪几组、每组有哪些值」由 {@link groupSkuAttributes} 算，
 * 「某个值还有没有货」由 {@link isOptionAvailable} 算 —— 后者会把<b>已选的其它规格</b>
 * 一起算进去：选了「珍珠白」之后，只有白色那几个 SKU 里有货的尺码才该可选，
 * 否则用户选完两项才被告知没货。
 *
 * <p>无规格商品也有一行 SKU（`skuAttrs` 为空对象，DDL 明写），所以这一页对
 * 「有没有规格」的判断是看分组数，不是看 SKU 列表长不长。
 *
 * <h3>这一页不提交任何东西</h3>
 * 点「立即兑换」只是<b>带着选中的 SKU 与件数跳到兑换确认页</b>。
 * 兑换会真的扣积分，值得一个停顿。<b>没有购物车</b>：一单一 SKU。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// noUncheckedIndexedAccess 下 route.params.id 是 string | string[] | undefined
const rawId = route.params.id
const commodityId = toId(Array.isArray(rawId) ? (rawId[0] ?? '') : (rawId ?? ''))

const detail = useAsync(() => fetchCommodityDetail(commodityId))
const favorites = useFavorites()

/* ---- 规格 ---- */
const skus = computed(() => detail.data.value?.skus ?? [])
const groups = computed(() => groupSkuAttributes(skus.value))

/** 规格名 → 选中的值 */
const selections = ref<Record<string, string>>({})

function selectOption(name: string, value: string): void {
  if (!isOptionAvailable(skus.value, selections.value, name, value)) {
    return
  }
  // 再点一次取消，方便换一组重选 —— 否则选错了只能刷新
  const next = { ...selections.value }
  if (next[name] === value) {
    delete next[name]
  } else {
    next[name] = value
  }
  selections.value = next
}

const chosenSku = computed(() => findSku(skus.value, selections.value, groups.value.length))

const missingGroup = computed(
  () => groups.value.find((g) => selections.value[g.name] === undefined) ?? null,
)

/* ---- 件数 ---- */
const quantity = ref(1)

/**
 * 最多能兑几件：受<b>库存</b>与<b>限兑剩余</b>两头夹。
 * 没选 SKU 时用商品总可用库存兜个上限，免得加号一开始就点不动。
 */
const maxQuantity = computed(() => {
  const stock = chosenSku.value?.availableStock ?? detail.data.value?.availableStock ?? 0
  const remaining = detail.data.value?.remainingCount
  return Math.max(
    1,
    remaining === null || remaining === undefined ? stock : Math.min(stock, remaining),
  )
})

function changeQuantity(delta: number): void {
  quantity.value = Math.min(maxQuantity.value, Math.max(1, quantity.value + delta))
}

/* ---- 展示 ---- */
const cost = computed(() => {
  const data = detail.data.value
  if (data === null) {
    return ''
  }
  // 选了 SKU 就按 SKU 的价（不同规格可以不同价），没选按商品基准价
  const points = chosenSku.value?.pointsPrice ?? data.pointsPrice
  const cash = chosenSku.value?.cashPrice ?? data.cashPrice
  return formatCost(data.payType, points, cash)
})

const worth = computed(() =>
  detail.data.value === null ? '' : formatWorth(detail.data.value.originalPrice),
)

const availableStock = computed(
  () => chosenSku.value?.availableStock ?? detail.data.value?.availableStock ?? 0,
)

/** 限兑文案。limitCount=0 表示不限，这时整行不显示 */
const limitText = computed(() => {
  const data = detail.data.value
  if (data === null || data.limitCount <= 0) {
    return ''
  }
  const period = {
    LIFETIME: '每人限兑',
    DAILY: '每日限兑',
    WEEKLY: '每周限兑',
    MONTHLY: '每月限兑',
  }[data.limitPeriod]
  const remaining = data.remainingCount === null ? '' : `，还可兑 ${data.remainingCount} 件`
  return `${period} ${data.limitCount} 件${remaining}`
})

/** 占位块上的字：优先取「样例·」这类前缀之后的首字 */
const initial = computed(() => {
  const name = detail.data.value?.commodityName ?? ''
  const tail = name.split('·').pop() ?? name
  return tail.trim().charAt(0) || '?'
})

/** 按钮为什么不能点，要说出来 —— 一个没有解释的置灰按钮等于死路 */
const blockedReason = computed<string | null>(() => {
  const data = detail.data.value
  if (data === null) {
    return null
  }
  if (data.availableStock <= 0) {
    return '该商品已兑完'
  }
  if (data.remainingCount !== null && data.remainingCount <= 0) {
    return '本周期兑换次数已用完'
  }
  if (missingGroup.value !== null) {
    return `请选择${missingGroup.value.name}`
  }
  if (chosenSku.value === null) {
    return '该规格组合暂不可选'
  }
  if (chosenSku.value.availableStock <= 0) {
    return '该规格已兑完'
  }
  return null
})

function onRedeem(): void {
  const sku = chosenSku.value
  if (blockedReason.value !== null || sku === null) {
    return
  }
  if (!auth.isLoggedIn) {
    // 兑换要登录。带 redirect 回来，登完就在这一页
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  /*
   * 走 query 而不是 store：刷新不丢，从兑换页去地址簿挑地址再回来也不丢。
   * 兑换页会拿商品详情重新校验一遍 —— query 是用户能改的。
   */
  void router.push({
    name: 'redeem',
    params: { id: commodityId },
    query: { sku: sku.skuId, qty: String(quantity.value) },
  })
}

function goBack(): void {
  router.back()
}
</script>

<template>
  <div class="page">
    <button class="back" type="button" aria-label="返回" @click="goBack">
      <Icon name="chevron" :size="22" style="transform: scaleX(-1)" />
    </button>

    <div v-if="detail.loading.value" class="state" aria-hidden="true">
      <span class="state__dot" />
    </div>

    <div v-else-if="detail.error.value !== null" class="state state--error" role="alert">
      <p class="state__text">{{ detail.error.value }}</p>
      <Button variant="text" @click="detail.reload">重试</Button>
    </div>

    <template v-else-if="detail.data.value !== null">
      <!--
        主图区。bannerFileIds 为空时只有一张占位块，不画图集圆点 ——
        一个滑不动的轮播比没有更糟。等文件下载接口通了换成图集。
      -->
      <div class="media">
        <span class="media__initial" aria-hidden="true">{{ initial }}</span>
        <button
          class="media__fav"
          type="button"
          :class="{
            'media__fav--on': favorites.isFavorite(commodityId, detail.data.value.favorite),
          }"
          :disabled="favorites.pending.value !== null"
          :aria-pressed="favorites.isFavorite(commodityId, detail.data.value.favorite)"
          :aria-label="
            favorites.isFavorite(commodityId, detail.data.value.favorite) ? '取消收藏' : '收藏'
          "
          @click="favorites.toggle(commodityId, detail.data.value.favorite)"
        >
          <Icon name="heart" :size="20" />
        </button>
      </div>

      <div class="body">
        <h1 class="title">{{ detail.data.value.commodityName }}</h1>
        <p v-if="detail.data.value.commodityIntro !== null" class="intro">
          {{ detail.data.value.commodityIntro }}
        </p>

        <div class="price">
          <span class="price__now">{{ cost }}</span>
          <s v-if="worth !== ''" class="price__was">{{ worth }}</s>
          <!-- 库存既有颜色也有文字：只靠颜色区分对色觉障碍用户等于没区分 -->
          <span class="price__stock" :class="{ 'price__stock--out': availableStock <= 0 }">
            {{ availableStock > 0 ? `现货 ${availableStock} 件` : '已兑完' }}
          </span>
        </div>

        <p v-if="limitText !== ''" class="limit">{{ limitText }}</p>

        <!-- 规格。无规格商品（分组数为 0）整段不渲染 -->
        <section
          v-for="group in groups"
          :key="group.name"
          class="attr"
          role="group"
          :aria-label="group.name"
        >
          <h2 class="attr__title">{{ group.name }}</h2>
          <div class="attr__options">
            <button
              v-for="value in group.values"
              :key="value"
              type="button"
              class="opt"
              :class="{
                'opt--on': selections[group.name] === value,
                'opt--out': !isOptionAvailable(skus, selections, group.name, value),
              }"
              :disabled="!isOptionAvailable(skus, selections, group.name, value)"
              :aria-pressed="selections[group.name] === value"
              :aria-label="
                isOptionAvailable(skus, selections, group.name, value) ? value : `${value}（无货）`
              "
              @click="selectOption(group.name, value)"
            >
              {{ value }}
            </button>
          </div>
        </section>

        <!-- 件数 -->
        <section class="attr" role="group" aria-label="兑换件数">
          <h2 class="attr__title">兑换件数</h2>
          <div class="qty">
            <button
              class="qty__btn"
              type="button"
              aria-label="减少一件"
              :disabled="quantity <= 1"
              @click="changeQuantity(-1)"
            >
              −
            </button>
            <span class="qty__value" aria-live="polite">{{ quantity }}</span>
            <button
              class="qty__btn"
              type="button"
              aria-label="增加一件"
              :disabled="quantity >= maxQuantity"
              @click="changeQuantity(1)"
            >
              +
            </button>
          </div>
        </section>

        <section v-if="detail.data.value.exchangeNotice !== null" class="notice">
          <h2 class="attr__title">兑换须知</h2>
          <p class="notice__text">{{ detail.data.value.exchangeNotice }}</p>
        </section>

        <section v-if="detail.data.value.detailContent !== null" class="about">
          <h2 class="attr__title">商品介绍</h2>
          <!--
            图文详情是运营在后台配的富文本 HTML，来自我们自己的后端，按可信内容渲染
            （和活动规则正文同一个口径）。刻意不引 sanitize 库。
          -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div class="about__body" v-html="detail.data.value.detailContent"></div>
        </section>
      </div>
    </template>

    <div v-if="detail.data.value !== null" class="bar">
      <p v-if="favorites.error.value !== ''" class="bar__hint" role="alert">
        {{ favorites.error.value }}
      </p>
      <p v-else-if="blockedReason !== null" class="bar__hint">{{ blockedReason }}</p>

      <Button :disabled="blockedReason !== null" @click="onRedeem">立即兑换</Button>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100dvh;
  padding-bottom: calc(96px + var(--sv-safe-bottom));
}

.back {
  position: fixed;
  top: calc(var(--sv-safe-top) + var(--sv-space-sm));
  left: var(--sv-space-page);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: var(--sv-bg-surface);
  color: var(--sv-text-primary);
  cursor: pointer;
}

.back:active {
  background: var(--sv-bg-pressed);
}

.back:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

.state {
  display: flex;
  justify-content: center;
  padding-top: 40vh;
}

.state--error {
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-md);
  padding: 40vh var(--sv-space-page) 0;
}

.state__text {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.state__dot {
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
  .state__dot {
    animation-duration: 2.4s;
  }
}

/* ---- 主图 ---- */
.media {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 42vh;
  min-height: 260px;
  border-radius: 0 0 32px 32px;
  background: var(--sv-bg-surface);
}

.media__initial {
  color: var(--sv-text-placeholder);
  font-size: 96px;
  font-weight: 700;
  line-height: 1;
}

.media__fav {
  position: absolute;
  right: var(--sv-space-page);
  bottom: var(--sv-space-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: var(--sv-bg-page);
  color: var(--sv-text-placeholder);
  cursor: pointer;
}

.media__fav--on {
  color: var(--sv-color-primary);
}

.media__fav--on :deep(.sv-icon) {
  fill: currentcolor;
}

.media__fav:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

/* ---- 正文 ---- */
.body {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-lg);
  padding: var(--sv-space-lg) var(--sv-space-page) 0;
}

.title {
  margin: 0;
  font-size: var(--sv-font-title);
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.25;
}

.intro,
.price,
.limit {
  /* 紧跟标题的几行不吃 body 的 gap，自己贴上去 */
  margin-top: calc(var(--sv-space-lg) * -1 + var(--sv-space-sm));
}

.intro {
  margin-bottom: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.price {
  display: flex;
  align-items: baseline;
  gap: var(--sv-space-sm);
}

.price__now {
  color: var(--sv-color-primary);
  font-size: var(--sv-font-heading);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.price__was {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
  font-variant-numeric: tabular-nums;
}

.price__stock {
  margin-left: auto;
  color: var(--sv-color-success);
  font-size: var(--sv-font-footnote);
}

.price__stock--out {
  color: var(--sv-text-placeholder);
}

.limit {
  margin-bottom: 0;
  color: var(--sv-color-warning);
  font-size: var(--sv-font-footnote);
}

.attr__title {
  margin: 0 0 var(--sv-space-sm);
  font-size: var(--sv-font-body);
  font-weight: 600;
}

.attr__options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sv-space-sm);
}

.opt {
  min-width: 48px;
  height: 40px;
  padding: 0 var(--sv-space-md);
  border: 1px solid var(--sv-border-color);
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-surface);
  color: var(--sv-text-primary);
  font: inherit;
  font-size: var(--sv-font-caption);
  cursor: pointer;
}

/* 选中态既换底色也换边框：只靠一样在浅色底上不够显眼 */
.opt--on {
  border-color: var(--sv-color-primary);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-weight: 600;
}

/*
 * 无货的规格仍然展示但禁选，让用户知道确实有这个规格 ——
 * 直接隐藏的话用户会以为自己记错了。斜线比单纯变淡更明确。
 */
.opt--out {
  cursor: default;
  opacity: 0.45;
  background-image: linear-gradient(
    to top right,
    transparent calc(50% - 1px),
    var(--sv-text-placeholder) calc(50% - 1px),
    var(--sv-text-placeholder) calc(50% + 1px),
    transparent calc(50% + 1px)
  );
}

.opt:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

/* ---- 件数 ---- */
.qty {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
}

.qty__btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--sv-border-color);
  border-radius: var(--sv-radius-sm);
  background: var(--sv-bg-surface);
  color: var(--sv-text-primary);
  font: inherit;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.qty__btn:disabled {
  cursor: default;
  opacity: 0.4;
}

.qty__btn:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

.qty__value {
  min-width: 32px;
  text-align: center;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ---- 须知与详情 ---- */
.notice__text {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.7;
}

.about__body {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.7;
}

.about__body :deep(p) {
  margin: 0 0 var(--sv-space-sm);
}

.about__body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--sv-radius-md);
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
</style>
