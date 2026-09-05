<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { fetchPromos } from '@/api/promo'
import { useAsync } from '@/composables/useAsync'

/**
 * 活动中心。首页三个 tab 里的第三个。
 *
 * <p>这一页原来是底部导航的「优惠」（PromoView）。搬进首页顶部 tab 之后底部那个入口
 * 就去掉了 —— 同一份数据留两个入口，迟早会出现「一边改了一边没改」。
 * 老路径 `/promo` 在 router 里 redirect 到这里，收藏过的链接不会失效。
 */

const promos = useAsync(fetchPromos)
</script>

<template>
  <div class="activities">
    <Section
      title="进行中"
      :loading="promos.loading.value"
      :error="promos.error.value"
      :empty="(promos.data.value ?? []).length === 0"
      empty-text="暂时没有进行中的活动，过两天再来看看"
      @retry="promos.reload"
    >
      <div class="list">
        <!--
          只有 joinable 的活动才渲染成链接（RouterLink）。未开始的仍是 div ——
          做成能点但点进去啥也参与不了的链接是在骗用户。
          joinable 由服务端时钟算好下发，不让客户端拿本地时间自己判。
        -->
        <component
          :is="promo.joinable ? RouterLink : 'div'"
          v-for="promo in promos.data.value ?? []"
          :key="promo.activityCode"
          class="promo"
          :to="
            promo.joinable ? { name: 'activity', params: { code: promo.activityCode } } : undefined
          "
        >
          <div class="promo__body">
            <div class="promo__row">
              <h3 class="promo__name">{{ promo.activityName }}</h3>
              <span class="promo__badge" :class="{ 'promo__badge--live': promo.joinable }">
                {{ promo.joinable ? '进行中' : '未开始' }}
              </span>
            </div>
            <p v-if="promo.subTitle !== null" class="promo__sub">{{ promo.subTitle }}</p>
            <p class="promo__time">{{ promo.endTime }} 结束</p>
          </div>
          <Icon v-if="promo.joinable" name="chevron" :size="18" class="promo__arrow" />
        </component>
      </div>
    </Section>
  </div>
</template>

<style scoped>
.activities {
  padding: var(--sv-space-md) var(--sv-space-page) 0;
}

.list {
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

.promo__arrow {
  flex: none;
  color: var(--sv-text-placeholder);
}

.promo__row {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
}

.promo__name {
  margin: 0;
  font-size: var(--sv-font-body);
  font-weight: 600;
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

.promo__sub {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.promo__time {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}
</style>
