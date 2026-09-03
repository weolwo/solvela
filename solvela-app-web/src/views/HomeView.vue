<script setup lang="ts">
import { computed } from 'vue'

import { fetchAssets, fetchRecords } from '@/api/home'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { formatWithSeparator, money } from '@/utils/money'

const auth = useAuthStore()

const assets = useAsync(fetchAssets)
const records = useAsync(fetchRecords)

/**
 * 主资产（列表第一项）单独放大展示。哪一项是主资产由**后端的顺序**决定，
 * 前端不硬编码「SCORE 是主的」—— 那是业务配置，会变。
 */
const primaryAsset = computed(() => assets.data.value?.[0] ?? null)
const otherAssets = computed(() => assets.data.value?.slice(1) ?? [])

/**
 * 金额从后端来是字符串，展示要走 money 工具。
 * 🔴 不要 Number() 之后 toFixed —— 那会在超过 2^53-1 时静默丢精度，
 * 而余额正是最不该丢精度的数。
 */
function display(amount: string, currency: boolean): string {
  return currency ? formatWithSeparator(money(amount)) : amount
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <p class="page__greeting">你好</p>
      <h1 class="page__name">{{ auth.member?.nickname ?? '—' }}</h1>
    </header>

    <!-- 资产卡：整页最重的一块，用主色实底把它和下面的白卡分开 -->
    <div class="wallet">
      <p class="wallet__label">{{ primaryAsset?.label ?? '资产' }}</p>
      <p v-if="assets.loading.value" class="wallet__amount wallet__amount--loading">—</p>
      <p v-else-if="assets.error.value !== null" class="wallet__error">
        {{ assets.error.value }}
        <button class="wallet__retry" type="button" @click="assets.reload">重试</button>
      </p>
      <p v-else class="wallet__amount">
        {{ primaryAsset === null ? '—' : display(primaryAsset.amount, primaryAsset.currency) }}
      </p>

      <div v-if="otherAssets.length > 0" class="wallet__more">
        <div v-for="item in otherAssets" :key="item.assetType" class="wallet__chip">
          <span>{{ item.label }}</span>
          <span class="wallet__chip-value">{{ display(item.amount, item.currency) }}</span>
        </div>
      </div>
    </div>

    <SvSection
      title="订单记录"
      :loading="records.loading.value"
      :error="records.error.value"
      :empty="(records.data.value ?? []).length === 0"
      empty-text="还没有记录，去优惠页看看有什么活动"
      @retry="records.reload"
    >
      <SvCard>
        <div v-for="item in records.data.value ?? []" :key="item.id" class="record">
          <div class="record__main">
            <p class="record__title">{{ item.title }}</p>
            <p class="record__time">{{ item.createTime }}</p>
          </div>
          <div class="record__side">
            <span v-if="item.amount !== null" class="record__amount">
              +{{ formatWithSeparator(money(item.amount)) }}
            </span>
            <span class="record__status" :class="`record__status--${item.status.toLowerCase()}`">
              {{ item.statusText }}
            </span>
          </div>
        </div>
      </SvCard>
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

.page__greeting {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.page__name {
  margin: var(--sv-space-xs) 0 0;
  font-size: var(--sv-font-title);
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.wallet {
  padding: var(--sv-space-lg);
  border-radius: var(--sv-radius-lg);
  background: linear-gradient(150deg, #ff6a4d, var(--sv-color-primary) 55%, #d92f1f);
  color: var(--sv-text-on-primary);
}

.wallet__label {
  margin: 0;
  font-size: var(--sv-font-caption);
  opacity: 0.88;
}

.wallet__amount {
  margin: var(--sv-space-sm) 0 0;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.1;
  /* 数字等宽：加载完成后位数变化时不会让整块跳动 */
  font-variant-numeric: tabular-nums;
}

.wallet__amount--loading {
  opacity: 0.5;
}

.wallet__error {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
  margin: var(--sv-space-sm) 0 0;
  font-size: var(--sv-font-caption);
}

.wallet__retry {
  border: 1px solid currentcolor;
  padding: 2px var(--sv-space-sm);
  border-radius: var(--sv-radius-pill);
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: var(--sv-font-footnote);
  cursor: pointer;
}

.wallet__more {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-md);
}

.wallet__chip {
  display: flex;
  align-items: baseline;
  gap: var(--sv-space-xs);
  padding: var(--sv-space-xs) var(--sv-space-sm);
  border-radius: var(--sv-radius-pill);
  background: rgb(255 255 255 / 20%);
  font-size: var(--sv-font-footnote);
}

.wallet__chip-value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.record {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
}

.record__main {
  flex: 1;
  min-width: 0;
}

.record__title {
  margin: 0;
  font-size: var(--sv-font-caption);
  /* 奖品名可能很长，一行截断好过把整行撑成两行高低不齐 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record__time {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}

.record__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sv-space-xs);
}

.record__amount {
  color: var(--sv-color-primary);
  font-size: var(--sv-font-caption);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 状态既有颜色也有文字：只靠颜色区分对色觉障碍用户等于没区分 */
.record__status {
  font-size: var(--sv-font-footnote);
}

.record__status--done {
  color: var(--sv-color-success);
}

.record__status--pending {
  color: var(--sv-color-warning);
}

.record__status--failed {
  color: var(--sv-color-danger);
}
</style>
