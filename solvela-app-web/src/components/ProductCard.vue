<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import type { CommodityBrief } from '@/api/mall'
import { formatCost, formatDiscount, formatListPoints, formatWorth } from '@/utils/cost'

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
 * <h3>折扣角标只有等级折扣这一种（2026-09-23）</h3>
 * 在此之前这里写着「没有折扣角标」，理由是<b>不存在一个可以算百分比的基准</b> ——
 * 运营的「优惠」就是直接调低 `points_price`，而 `original_price` 是「值多少钱」（现金），
 * 拿它和积分价相除得不到任何有意义的数。那句话当时是对的。
 *
 * <p>等级价造出了那个基准：`listPointsPrice` 是挂牌价，`pointsPrice` 是这个人要付的价，
 * 两者之差<b>就是这个人因为等级省下的分</b>。所以角标现在有了，但仅此一种 ——
 * 运营调价<b>仍然</b>不产生角标，那不是优惠，那是改价。
 *
 * <h3>没有图时画首字，不画灰方块</h3>
 * `coverUrl` 为 null 是正常的（没配图，或文件被删）。占位块用商品名首字，
 * 和 MineView 头像的首字兜底同一套做法 —— 比一个灰方块有辨识度，
 * 也不会被误认成图裂了。
 *
 * <p>图加载失败时<b>退回同一个占位块</b>：一张裂图比没有图更像 bug，
 * 而用户对此无能为力。
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
  formatCost(
    props.commodity.payType,
    props.commodity.pointsPrice,
    props.commodity.cashPrice,
    props.commodity.priceVaries,
  ),
)

/** 为空表示运营没配划线价，那一行整个不渲染 */
const worth = computed(() => formatWorth(props.commodity.originalPrice))

const soldOut = computed(() => props.commodity.availableStock <= 0)

/** 等级折扣角标，如 `8.8折`。这件商品没便宜就为空 */
const discountTag = computed(() =>
  formatDiscount(
    props.commodity.pointsPrice,
    props.commodity.listPointsPrice,
    props.commodity.gradeDiscountPercent,
  ),
)

/** 划掉的挂牌积分价。没享到折扣就为空 */
const listCost = computed(() =>
  formatListPoints(props.commodity.pointsPrice, props.commodity.listPointsPrice),
)

/**
 * 专享商品的角标文案。不是专享就为空。
 *
 * 🔴 这一条<b>不藏商品</b>：一件看得见但换不了的商品，正是「够上去」的理由本身。
 * 藏起来的话，用户永远不知道升到白金能换到什么 ——
 * 而给用户一个够上去的理由，就是整套等级体系要换的东西。
 * （任务中心那边是藏的，因为一个点不动的任务没有这种吸引力，两处刻意不同。）
 *
 * ⚠️ `gradeLocked` 是服务端算好的结论，端上不自己拿等级去比 ——
 * 保级缓冲期那种「在白金但成长值够不着白金」端上判不了。
 */
const exclusiveTag = computed(() => {
  const c = props.commodity
  if (!c.gradeLocked) {
    return ''
  }
  return c.minGradeName === null ? '等级专享' : `${c.minGradeName}专享`
})

/*
 * 图加载失败就退回首字占位。不用 v-if 判 URL 就够了 ——
 * 服务端给的 URL 指向一个已被删掉的文件时，请求会 404 而 URL 本身非空。
 */
const coverBroken = ref(false)
</script>

<template>
  <article class="card">
    <RouterLink class="card__link" :to="{ name: 'product', params: { id: commodity.commodityId } }">
      <div class="card__media">
        <img
          v-if="commodity.coverUrl !== null && !coverBroken"
          class="card__img"
          :src="commodity.coverUrl"
          :alt="commodity.commodityName"
          loading="lazy"
          decoding="async"
          @error="coverBroken = true"
        />
        <span v-else class="card__initial" aria-hidden="true">{{ initial }}</span>
        <!--
          兑完和专享同时成立时先说专享：对用户来说「我还不够格」
          和「手慢了」的下一步动作完全不同。
        -->
        <span v-if="exclusiveTag !== ''" class="card__lock">{{ exclusiveTag }}</span>
        <span v-else-if="soldOut" class="card__out">已兑完</span>
      </div>

      <h3 class="card__title">{{ commodity.commodityName }}</h3>
      <p class="card__cost">{{ cost }}</p>
      <!--
        🔴 折扣角标放在【划线那一行】，不放在价格行。
        价格行是会截断的（「45,000 积分 + ¥299.00」放不下就 ellipsis），
        角标塞进去之后，积分+现金的商品上它会被裁掉一半 —— 联调时就是这么发现的。
        而且划线价本来就是解释折扣的那一行，角标贴着它反而更说得通。

        两个划线位只放得下一个，享到等级折扣时优先显示「原本要多少分」：
        「省了 1,600 积分」比「价值 ¥1,999」对这一刻的决定更有用 ——
        后者说的是商品值不值，前者说的是【他因为是白金而少付了多少】。
      -->
      <p v-if="listCost !== ''" class="card__worth card__worth--deal">
        <s class="card__worth-was">{{ listCost }}</s>
        <span v-if="discountTag !== ''" class="card__discount">{{ discountTag }}</span>
      </p>
      <p v-else-if="worth !== ''" class="card__worth">{{ worth }}</p>
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

.card__img {
  width: 100%;
  height: 100%;
  /* 商品图长宽比不一：cover 让卡片始终是正方形，不被一张竖图撑变形 */
  object-fit: cover;
  border-radius: inherit;
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
.card__lock {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 45%);
  color: #f5d28a;
  font-size: var(--sv-font-caption);
  font-weight: 600;
  letter-spacing: 0.02em;
}

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

.card__worth--deal {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 划线只画在价上，不画在角标上 */
.card__worth-was {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__discount {
  /* 🔴 不许被压缩：它是这张卡最该看见的东西，放不下就让划线价去截断 */
  flex: 0 0 auto;
  padding: 0 4px;
  border-radius: var(--sv-radius-sm);
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
  font-size: var(--sv-font-footnote);
  font-weight: 600;
  text-decoration: none;
  /* 角标不参与 tabular-nums：「8.8折」里的折字会被撑开 */
  font-variant-numeric: normal;
}
</style>
