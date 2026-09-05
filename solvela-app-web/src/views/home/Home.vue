<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { fetchCommodities } from '@/api/mall'
import { fetchBanners, fetchPromos } from '@/api/promo'
import ProductCard from '@/components/ProductCard.vue'
import { useAsync } from '@/composables/useAsync'
import { useFavorites } from '@/composables/useFavorites'
import type { CarouselItem } from '@/ui/Carousel.vue'

/**
 * 首页。顶部四个 tab 里的第一个，也是 `/` 的落点。
 *
 * <h3>这一页是「入口」，不是「列表」</h3>
 * 轮播图只在这里出现；商品和活动都<b>只取前几条</b>，看全的入口在旁边的 tab 里。
 * 首页要回答的是「现在有什么值得看」，不是「全部有什么」——
 * 把完整列表也铺在这里，商城和活动中心两个 tab 就没有存在的理由了。
 *
 * <h3>为什么不复用商城的那次请求</h3>
 * 两个 pane 各自 useAsync 一次，看起来是重复请求。真接上后端之后<b>本来就该是两个接口</b>：
 * 首页要的是「推荐位」（运营排序、几条），商城要的是「商品列表」（分页、可筛选）。
 * 现在共用一个桩不代表将来共用一个接口，做缓存共享是提前给一个不存在的形状买单。
 */

const router = useRouter()

const banners = useAsync(fetchBanners)
/** 首页只露一排半，看全的去商城 */
const HIGHLIGHT_COUNT = 4

// 首页只要前几件，直接按 pageSize 要 —— 不是拿回全部再 slice
const commodities = useAsync(() => fetchCommodities({ pageSize: HIGHLIGHT_COUNT }))
const promos = useAsync(fetchPromos)

const favorites = useFavorites()

/** 首页只列最近的两场，看全的去活动中心 */
const PROMO_COUNT = 2

/**
 * 活动 → 焦点位卡片。这一步<b>刻意留在页面</b>：
 * 「一条活动画成什么样的卡」是展示决策，api 层不该 import 任何 .vue 来说明它。
 */
const bannerItems = computed<CarouselItem[]>(() =>
  (banners.data.value ?? []).map((p) => ({
    id: p.activityCode,
    title: p.activityName,
    subtitle: p.subTitle,
    themeColor: p.themeColor,
    // 没开始的活动不给按钮：做成能点但点进去啥也参与不了的入口是在骗用户
    ctaLabel: p.joinable ? '立即参与' : null,
  })),
)

const highlights = computed(() => commodities.data.value?.list ?? [])
const topPromos = computed(() => (promos.data.value ?? []).slice(0, PROMO_COUNT))

/** 焦点位的按钮点了去哪，由页面决定 —— Carousel 只抛出那张卡的 id（这里就是活动编码） */
function onBannerCta(activityCode: string): void {
  void router.push({ name: 'activity', params: { code: activityCode } })
}
</script>

<template>
  <div class="home-feed">
    <!--
      运营焦点位。装饰性内容，出错不值得打断页面或给个「重试」按钮打扰用户 ——
      静默隐藏就好，用户看到的只是少了一块推荐，不是功能坏了。
      加载中用一根骨架条占位，避免它加载完成的一瞬间把下面内容顶下去一截。
    -->
    <div v-if="banners.loading.value" class="banner-skeleton" aria-hidden="true"></div>
    <Carousel v-else-if="!banners.error.value" :items="bannerItems" @cta="onBannerCta" />

    <p v-if="favorites.error.value !== ''" class="home-feed__error" role="alert">
      {{ favorites.error.value }}
    </p>

    <Section
      title="精选好物"
      :loading="commodities.loading.value"
      :error="commodities.error.value"
      :empty="highlights.length === 0"
      empty-text="还没有上架的商品"
      @retry="commodities.reload"
    >
      <template #action>
        <RouterLink class="more" :to="{ name: 'mall' }">
          逛商城
          <Icon name="chevron" :size="14" />
        </RouterLink>
      </template>

      <div class="grid">
        <ProductCard
          v-for="item in highlights"
          :key="item.commodityId"
          :commodity="item"
          :favorite="favorites.isFavorite(item.commodityId, item.favorite)"
          :busy="favorites.pending.value !== null"
          @toggle-favorite="favorites.toggle(item.commodityId, item.favorite)"
        />
      </div>
    </Section>

    <Section
      title="进行中的活动"
      :loading="promos.loading.value"
      :error="promos.error.value"
      :empty="topPromos.length === 0"
      empty-text="暂时没有进行中的活动"
      @retry="promos.reload"
    >
      <template #action>
        <RouterLink class="more" :to="{ name: 'activities' }">
          全部活动
          <Icon name="chevron" :size="14" />
        </RouterLink>
      </template>

      <div class="promos">
        <!--
          只有 joinable 的活动才渲染成链接（RouterLink）。未开始的仍是 div ——
          做成能点但点进去啥也参与不了的链接是在骗用户。
          joinable 由服务端时钟算好下发，不让客户端拿本地时间自己判。
        -->
        <component
          :is="promo.joinable ? RouterLink : 'div'"
          v-for="promo in topPromos"
          :key="promo.activityCode"
          class="promo"
          :to="
            promo.joinable ? { name: 'activity', params: { code: promo.activityCode } } : undefined
          "
        >
          <div class="promo__body">
            <h3 class="promo__name">{{ promo.activityName }}</h3>
            <p v-if="promo.subTitle !== null" class="promo__sub">{{ promo.subTitle }}</p>
          </div>
          <span class="promo__badge" :class="{ 'promo__badge--live': promo.joinable }">
            {{ promo.joinable ? '进行中' : '未开始' }}
          </span>
        </component>
      </div>
    </Section>
  </div>
</template>

<style scoped>
.home-feed {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-lg);
  padding: var(--sv-space-md) var(--sv-space-page) 0;
}

/* 高度对齐 Carousel 一张卡的 min-height，加载完成切换时不跳动 */
.banner-skeleton {
  height: 132px;
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  opacity: 0.7;
  animation: sv-pulse 1.4s ease-in-out infinite;
}

@keyframes sv-pulse {
  50% {
    opacity: 0.35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .banner-skeleton {
    animation: none;
  }
}

.home-feed__error {
  margin: 0;
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

.more {
  display: flex;
  align-items: center;
  gap: 2px;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
  text-decoration: none;
}

.more:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
  border-radius: var(--sv-radius-sm);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sv-space-md);
}

.promos {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
}

.promo {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  color: var(--sv-text-primary);
  text-decoration: none;
}

/* :active 只在链接态有意义（div 版点了不跳） */
a.promo:active {
  background: var(--sv-bg-pressed);
}

a.promo:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: -2px;
}

.promo__body {
  flex: 1;
  min-width: 0;
}

.promo__name {
  margin: 0;
  font-size: var(--sv-font-body);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.promo__sub {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 角标既有底色也有文字：只靠颜色区分对色觉障碍用户等于没区分 */
.promo__badge {
  flex: none;
  padding: 1px var(--sv-space-sm);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
}

.promo__badge--live {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}
</style>
