<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { fetchCategories, fetchCommodities } from '@/api/mall'
import ProductCard from '@/components/ProductCard.vue'
import { useAsync } from '@/composables/useAsync'
import { useFavorites } from '@/composables/useFavorites'
import type { Id } from '@/types/contract'

/**
 * 商城。顶部四个 tab 里的第二个。
 *
 * <p>轮播图<b>不在这里</b> —— 那是首页的东西。两个 tab 各画一份轮播的话，
 * 切过去切过来看到的是同一块图，tab 就白分了。
 *
 * <h3>🔴 搜索与分类筛选都在服务端，不是本地过滤</h3>
 * 第一版是拿回全部再 `filter`。接上后端之后那个做法<b>立刻就是错的</b>：
 * 接口是分页的，本地过滤只能搜到当前页，而用户不会知道自己搜的是「这 20 条」。
 * 所以关键词与分类作为参数传下去，由 `idx_mall_cmd_cat_status_sort` 去查。
 *
 * <p>关键词<b>加了防抖</b>：现在每敲一个字都是一次请求，不防抖就是往后端刷。
 * （第一版刻意没防抖，因为那时是本地过滤、防抖只会让它看着像在发请求。
 * 判据变了，做法就跟着变。）
 *
 * <h3>分类图标是后端给的文件，不是前端映射的图标名</h3>
 * `t_mall_category.icon_file_id` 是运营配的一张图。网关还没暴露文件下载，
 * 所以现在画分类名首字的占位块。
 * 🔴 <b>不要退回「分类编码 → 内置图标」那种映射</b>：表里根本没有 categoryCode，
 * 而且运营每加一个分类都得改前端代码。
 */

/** 关键词防抖。300ms 是「打完一个词」和「还在打」之间比较稳的分界 */
const KEYWORD_DEBOUNCE_MS = 300

const categories = useAsync(fetchCategories)

/* ---- 筛选。变了就重新查 ---- */
/** null = 全部分类 */
const activeCategory = ref<Id | null>(null)
const keyword = ref('')
const searching = ref(false)

/** 真正发给后端的关键词。防抖之后才跟上 keyword */
const appliedKeyword = ref('')
let debounceTimer: ReturnType<typeof setTimeout> | undefined

watch(keyword, (value) => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    appliedKeyword.value = value.trim()
  }, KEYWORD_DEBOUNCE_MS)
})

const commodities = useAsync(() =>
  fetchCommodities({
    categoryId: activeCategory.value,
    keyword: appliedKeyword.value,
    pageSize: 50,
  }),
)

// 条件一变就重查。useAsync 不带缓存，reload 就是重新发一次
watch([activeCategory, appliedKeyword], () => {
  void commodities.reload()
})

const list = computed(() => commodities.data.value?.list ?? [])
const total = computed(() => commodities.data.value?.total ?? 0)

/** 有筛选条件时，空态该说「没搜到」而不是「还没有商品」 */
const filtered = computed(() => activeCategory.value !== null || appliedKeyword.value !== '')

const favorites = useFavorites()

function selectCategory(id: Id | null): void {
  activeCategory.value = activeCategory.value === id ? null : id
}

function closeSearch(): void {
  searching.value = false
  keyword.value = ''
}

/** 分类图标的占位：取分类名首字 */
function categoryInitial(name: string): string {
  return name.trim().charAt(0) || '?'
}
</script>

<template>
  <div class="mall">
    <!-- 搜索：收起时只是一个图标，展开才占一行。商城的内容比搜索框重要 -->
    <div class="search">
      <template v-if="searching">
        <Icon name="search" :size="18" class="search__icon" />
        <input
          v-model="keyword"
          class="search__input"
          type="search"
          placeholder="搜索商品"
          aria-label="搜索商品"
        />
        <button class="search__btn" type="button" aria-label="关闭搜索" @click="closeSearch">
          <Icon name="close" :size="18" />
        </button>
      </template>
      <button
        v-else
        class="search__btn search__btn--open"
        type="button"
        aria-label="搜索商品"
        @click="searching = true"
      >
        <Icon name="search" :size="20" />
      </button>
    </div>

    <Section
      title="商品分类"
      :loading="categories.loading.value"
      :error="categories.error.value"
      :empty="(categories.data.value ?? []).length === 0"
      empty-text="还没有分类"
      @retry="categories.reload"
    >
      <!--
        横向滚动而不是换行：分类会越加越多，换行会让首屏被分类挤走。
        没有做「查看全部」—— 全部分类本来就都在这条里，点了没去处的链接比没有更糟。
      -->
      <div class="cats" role="group" aria-label="商品分类">
        <button
          class="cats__item"
          type="button"
          :class="{ 'cats__item--active': activeCategory === null }"
          :aria-pressed="activeCategory === null"
          @click="selectCategory(null)"
        >
          <span class="cats__chip"><Icon name="home" :size="22" /></span>
          <span class="cats__label">全部</span>
        </button>
        <button
          v-for="cat in categories.data.value ?? []"
          :key="cat.id"
          class="cats__item"
          type="button"
          :class="{ 'cats__item--active': activeCategory === cat.id }"
          :aria-pressed="activeCategory === cat.id"
          @click="selectCategory(cat.id)"
        >
          <!-- 等文件下载接口通了，这一块换成 <img :src="…iconFileId"> -->
          <span class="cats__chip cats__chip--text">{{ categoryInitial(cat.categoryName) }}</span>
          <span class="cats__label">{{ cat.categoryName }}</span>
        </button>
      </div>
    </Section>

    <p v-if="favorites.error.value !== ''" class="mall__error" role="alert">
      {{ favorites.error.value }}
    </p>

    <Section
      title="全部商品"
      :loading="commodities.loading.value"
      :error="commodities.error.value"
      :empty="list.length === 0"
      :empty-text="filtered ? '没有符合条件的商品，换个词或分类试试' : '还没有上架的商品'"
      @retry="commodities.reload"
    >
      <template #action>
        <span class="mall__count">{{ total }} 件</span>
      </template>

      <div class="grid">
        <ProductCard
          v-for="item in list"
          :key="item.commodityId"
          :commodity="item"
          :favorite="favorites.isFavorite(item.commodityId, item.favorite)"
          :busy="favorites.pending.value !== null"
          @toggle-favorite="favorites.toggle(item.commodityId, item.favorite)"
        />
      </div>
    </Section>
  </div>
</template>

<style scoped>
.mall {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-lg);
  padding: var(--sv-space-md) var(--sv-space-page) 0;
}

.search {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
  /* 收起时按钮靠右，展开时输入框占满 —— 两种状态高度一致，切换不跳动 */
  justify-content: flex-end;
  min-height: 40px;
  padding: 0 var(--sv-space-sm);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-bg-surface);
}

.search__icon {
  color: var(--sv-text-placeholder);
}

.search__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: var(--sv-font-caption);
  outline: none;
}

.search__btn {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--sv-text-secondary);
  cursor: pointer;
}

.search__btn--open {
  color: var(--sv-text-primary);
}

.search__btn:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

.mall__error {
  margin: 0;
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

.mall__count {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
  font-variant-numeric: tabular-nums;
}

/* ---- 分类条 ---- */
.cats {
  display: flex;
  gap: var(--sv-space-md);
  overflow-x: auto;
  padding-bottom: 2px;
  /* 滚动条在移动端本来就不显示，桌面上它会挤掉一点高度 */
  scrollbar-width: none;
}

.cats::-webkit-scrollbar {
  display: none;
}

.cats__item {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-xs);
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--sv-text-secondary);
  font: inherit;
  cursor: pointer;
}

.cats__chip {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.cats__chip--text {
  color: var(--sv-text-placeholder);
  font-size: 22px;
  font-weight: 700;
}

/* 选中态既换底色也加粗文字：只靠颜色区分对色觉障碍用户等于没区分 */
.cats__item--active .cats__chip {
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
}

.cats__item--active .cats__label {
  color: var(--sv-text-primary);
  font-weight: 600;
}

.cats__label {
  font-size: var(--sv-font-footnote);
}

.cats__item:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
  border-radius: var(--sv-radius-sm);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sv-space-md);
}
</style>
