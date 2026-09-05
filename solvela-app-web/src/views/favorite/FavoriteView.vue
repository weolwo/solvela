<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { fetchFavorites } from '@/api/mall'
import ProductCard from '@/components/ProductCard.vue'
import { useAsync } from '@/composables/useAsync'
import { useFavorites } from '@/composables/useFavorites'

/**
 * 我的收藏。从「我的」进来。
 *
 * <h3>取消收藏之后卡片<b>不立刻消失</b></h3>
 * 点错了的人马上还能点回来 —— 让它当场消失，撤销就得重新去商城把它找出来。
 * 心形变空已经足够说明状态变了，下次进这一页它自然就不在了。
 *
 * <p>所以这一页<b>不</b>在取消收藏后 reload：那等于把「消失」延迟半秒再演一遍，
 * 比立刻消失更让人困惑。
 */

const favorites = useAsync(fetchFavorites)
const toggle = useFavorites()
</script>

<template>
  <div class="page">
    <NavBar title="我的收藏" />

    <p v-if="toggle.error.value !== ''" class="page__hint" role="alert">
      {{ toggle.error.value }}
    </p>

    <Section
      title="收藏的商品"
      :loading="favorites.loading.value"
      :error="favorites.error.value"
      :empty="(favorites.data.value ?? []).length === 0"
      empty-text="还没有收藏的商品"
      @retry="favorites.reload"
    >
      <template #action>
        <span class="page__count">{{ (favorites.data.value ?? []).length }} 件</span>
      </template>

      <div class="grid">
        <ProductCard
          v-for="item in favorites.data.value ?? []"
          :key="item.commodityId"
          :commodity="item"
          :favorite="toggle.isFavorite(item.commodityId, item.favorite)"
          :busy="toggle.pending.value !== null"
          @toggle-favorite="toggle.toggle(item.commodityId, item.favorite)"
        />
      </div>
    </Section>

    <!--
      空态下给一条出路。Section 的 empty-text 只是一句话，
      而「还没有收藏」时用户真正需要的是「那去哪找商品」。
    -->
    <RouterLink
      v-if="!favorites.loading.value && (favorites.data.value ?? []).length === 0"
      class="page__cta"
      :to="{ name: 'mall' }"
    >
      去商城逛逛
    </RouterLink>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding-bottom: var(--sv-space-xl);
}

.page__hint {
  margin: 0 var(--sv-space-page);
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

.page__count {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
  font-variant-numeric: tabular-nums;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sv-space-md);
}

.page__cta {
  align-self: center;
  padding: var(--sv-space-sm) var(--sv-space-xl);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-size: var(--sv-font-caption);
  font-weight: 600;
  text-decoration: none;
}

.page__cta:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}
</style>
