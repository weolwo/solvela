<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type { CommodityBrief } from '@/api/mall'
import { formatCost, formatWorth } from '@/utils/cost'

/**
 * 一张商品卡。首页「精选好物」、商城网格、我的收藏三处用的是同一张 ——
 * 各画一遍的话，改个角标样式要改三处，而漏改的表现是三个页面长得不一样。
 *
 * <h3>为什么在 components/ 而不是 ui/ 或 views/xxx/</h3>
 * 三层分工：`ui/` 是对业务一无所知的原子组件（unplugin 自动注册）；
 * `components/` 是<b>跨页面</b>共用的业务组件（这个，必须显式 import）；
 * `views/&lt;page&gt;/` 是只有那一页用的私有零件。
 * 它认识 {@link CommodityBrief}，所以进不了 ui/。
 *
 * <h3>收藏按钮为什么是链接的兄弟节点，不在链接里</h3>
 * `<button>` 套在 `<a>` 里是无效 HTML，浏览器和读屏对「链接里的按钮」行为不一致
 *（有的把点击算给外层链接，直接跳走）。所以卡片是 `RouterLink` 覆盖图文区，
 * 收藏按钮绝对定位压在图上，两者<b>平级</b>。
 *
 * <h3>没有折扣角标</h3>
 * 表里没有 `discount_percent` —— 积分商城的「优惠」是运营直接调低 `points_price`，
 * 不存在一个可以算百分比的基准。`original_price` 是「值多少钱」（价值 ¥1,999），
 * 拿它和积分价相除得不到任何有意义的数。
 *
 * <h3>为什么没有图</h3>
 * 网关还没暴露文件下载接口，`coverFileId` 现在恒为 null。
 * 占位块用商品名首字，和 MineView 头像的首字兜底同一套做法 ——
 * 比一个灰方块有辨识度，也不会被误认成图裂了。
 */

const props = defineProps<{
  commodity: CommodityBrief
  /** 收藏与否由调用方给（服务端值可能被本地乐观更新覆盖，见 useFavorites） */
  favorite: boolean
  /** 有别的收藏请求在飞时禁用，避免连点把状态点乱 */
  busy: boolean
}>()

defineEmits<{ toggleFavorite: [] }>()

/** 占位块上的字：优先取「样例·」这类前缀之后的首字 */
const initial = computed(() => {
  const tail = props.commodity.commodityName.split('·').pop() ?? props.commodity.commodityName
  return tail.trim().charAt(0) || '?'
})

const cost = computed(() =>
  formatCost(props.commodity.payType, props.commodity.pointsPrice, props.commodity.cashPrice),
)

/** 为空表示运营没配划线价，那一行整个不渲染 */
const worth = computed(() => formatWorth(props.commodity.originalPrice))

const soldOut = computed(() => props.commodity.availableStock <= 0)
</script>

<template>
  <article class="card">
    <RouterLink class="card__link" :to="{ name: 'product', params: { id: commodity.commodityId } }">
      <div class="card__media">
        <!-- 商品图占位。等文件下载接口通了，这一块换成 <img :src="…coverFileId"> -->
        <span class="card__initial" aria-hidden="true">{{ initial }}</span>
        <span v-if="soldOut" class="card__out">已兑完</span>
      </div>

      <h3 class="card__title">{{ commodity.commodityName }}</h3>
      <p class="card__cost">{{ cost }}</p>
      <p v-if="worth !== ''" class="card__worth">{{ worth }}</p>
    </RouterLink>

    <button
      class="card__fav"
      type="button"
      :class="{ 'card__fav--on': favorite }"
      :disabled="busy"
      :aria-pressed="favorite"
      :aria-label="`${favorite ? '取消收藏' : '收藏'} ${commodity.commodityName}`"
      @click="$emit('toggleFavorite')"
    >
      <Icon name="heart" :size="16" />
    </button>
  </article>
</template>

<style scoped>
.card {
  position: relative;
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.card__link {
  display: flex;
  flex-direction: column;
  padding: var(--sv-space-sm);
  color: var(--sv-text-primary);
  text-decoration: none;
}

.card__link:active {
  background: var(--sv-bg-pressed);
  border-radius: var(--sv-radius-lg);
}

.card__link:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: -2px;
  border-radius: var(--sv-radius-lg);
}

.card__media {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-fill);
}

.card__initial {
  color: var(--sv-text-placeholder);
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
}

/* 无货既有文字也有位置（压在图上），不是只把卡片调淡 —— 那样看不出是什么原因 */
.card__out {
  position: absolute;
  top: var(--sv-space-xs);
  left: var(--sv-space-xs);
  padding: 1px var(--sv-space-xs);
  border-radius: var(--sv-radius-sm);
  background: var(--sv-bg-surface);
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-weight: 600;
}

.card__fav {
  /* 压在图上，但和 .card__link 平级 —— 见顶部注释 */
  position: absolute;
  top: calc(var(--sv-space-sm) + var(--sv-space-xs));
  right: calc(var(--sv-space-sm) + var(--sv-space-xs));
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 50%;
  background: var(--sv-bg-surface);
  color: var(--sv-text-placeholder);
  cursor: pointer;
}

.card__fav--on {
  color: var(--sv-color-primary);
}

/* 选中的心形填实：只靠描边色变化在 16px 上几乎看不出来 */
.card__fav--on :deep(.sv-icon) {
  fill: currentcolor;
}

.card__fav:disabled {
  cursor: default;
  opacity: 0.6;
}

.card__fav:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

.card__title {
  margin: var(--sv-space-sm) 0 0;
  font-size: var(--sv-font-caption);
  font-weight: 500;
  /* 商品名长短不一，统一截成一行，卡片高度才齐 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__cost {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-color-primary);
  font-size: var(--sv-font-caption);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  /* 「45,000 积分 + ¥299.00」比纯积分长，放不下就截，别把卡片撑成两行 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__worth {
  margin: 1px 0 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}
</style>
