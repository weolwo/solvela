<script setup lang="ts">
import { fetchPromos } from '@/api/home'
import { useAsync } from '@/composables/useAsync'

const promos = useAsync(fetchPromos)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">优惠</h1>
      <p class="page__subtitle">当前进行中的活动</p>
    </header>

    <SvSection
      title="进行中"
      :loading="promos.loading.value"
      :error="promos.error.value"
      :empty="(promos.data.value ?? []).length === 0"
      empty-text="暂时没有进行中的活动，过两天再来看看"
      @retry="promos.reload"
    >
      <div class="list">
        <!--
          ⚠️ 卡片现在【不是链接】：活动详情页还没有（那是抽奖页那一整块）。
          做成能点但点了没反应的链接是在骗用户，所以先渲染成 div。
          详情页落地后改成 <RouterLink :to="{ name: 'activity', params: { code } }">，
          并且只给 joinable 的那些加链接 —— joinable 由服务端时钟算好下发，
          不让客户端拿本地时间自己判。
        -->
        <div v-for="promo in promos.data.value ?? []" :key="promo.activityCode" class="promo">
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
        </div>
      </div>
    </SvSection>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-lg);
  padding: calc(var(--sv-safe-top) + var(--sv-space-lg)) var(--sv-space-page) var(--sv-space-lg);
}

.page__head {
  padding: 0 var(--sv-space-xs);
}

.page__title {
  margin: 0;
  font-size: var(--sv-font-title);
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.page__subtitle {
  margin: var(--sv-space-sm) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
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

.promo__body {
  flex: 1;
  min-width: 0;
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
