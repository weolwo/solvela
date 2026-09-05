<script setup lang="ts">
import { fetchPromoRecords } from '@/api/records'
import { useAsync } from '@/composables/useAsync'
import { formatWithSeparator, money } from '@/utils/money'

/**
 * 优惠记录：平台要发给我什么。
 *
 * <h3>底层是提案记录，但「提案」这个词一次都不出现</h3>
 * `t_proposal_record` 记的是「平台要发给你什么」—— 活动中奖、任务达标、
 * 人工补发都会落一条。叫提案是因为它要过审批，那是<b>运营视角</b>。
 * 用户不需要知道他的奖励要过两道审批。
 *
 * <h3>还在路上的也要出</h3>
 * 只出「已到账」的话，用户在等待期间会以为什么都没发生，
 * 然后去问客服。「处理中」这一行本身就是答案。
 */

const records = useAsync(fetchPromoRecords)
</script>

<template>
  <div class="page">
    <NavBar title="优惠记录" />

    <Section
      title="发给我的"
      :loading="records.loading.value"
      :error="records.error.value"
      :empty="(records.data.value ?? []).length === 0"
      empty-text="还没有优惠记录，去活动中心看看"
      @retry="records.reload"
    >
      <template #action>
        <span class="page__count">{{ (records.data.value ?? []).length }} 条</span>
      </template>

      <Card>
        <div v-for="item in records.data.value ?? []" :key="item.recordId" class="record">
          <div class="record__main">
            <p class="record__title">{{ item.title }}</p>
            <p class="record__time">{{ item.createTime }}</p>
          </div>
          <div class="record__side">
            <!-- 面值可能带小数（现金红包），走 money 工具；实物类没有面值 -->
            <span v-if="item.amount !== null" class="record__amount">
              +{{ formatWithSeparator(money(item.amount)) }}
            </span>
            <span class="record__status" :class="`record__status--${item.status.toLowerCase()}`">
              {{ item.statusText }}
            </span>
          </div>
        </div>
      </Card>
    </Section>
  </div>
</template>

<style scoped>
.page {
  padding-bottom: 24px;
}

.page__count {
  font-size: 13px;
  color: var(--sv-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.record {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
}

.record + .record {
  border-top: 1px solid var(--sv-border-subtle);
}

.record__main {
  min-width: 0;
}

.record__title {
  margin: 0;
  font-size: 15px;
  color: var(--sv-text-primary);
}

.record__time {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--sv-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.record__side {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.record__amount {
  font-size: 15px;
  font-weight: 600;
  color: var(--sv-text-primary);
  font-variant-numeric: tabular-nums;
}

.record__status {
  font-size: 12px;
}

.record__status--pending {
  color: var(--sv-color-warning);
}

.record__status--done {
  color: var(--sv-color-success);
}

.record__status--failed {
  color: var(--sv-color-danger);
}
</style>
