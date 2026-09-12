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
 *
 * <h3>🔴 2026-09-10 重做了视觉，修的是四个叠在一起的问题</h3>
 * <ul>
 *   <li><b>整页没有左右内边距</b> —— 卡片直接贴着屏幕边缘；</li>
 *   <li><b>Card 不自带 padding</b>（那是本项目的约定，由子元素给），
 *       而这里的行只写了上下 padding，于是文字紧贴卡片边框；</li>
 *   <li><b>分隔线用了不存在的变量</b> `--sv-border-subtle` ——
 *       border-color 非法会让<b>整条声明作废</b>，线根本没画出来，记录糊成一片；</li>
 *   <li><b>辅助文字用了不存在的</b> `--sv-text-tertiary` ——
 *       取不到值就继承父级，本该是浅灰的时间戳渲染成了正文深色。</li>
 * </ul>
 * 后两条现在由 `styles/__tests__/tokens.spec.ts` 盯着。
 */

const records = useAsync(fetchPromoRecords)
</script>

<template>
  <div class="page">
    <NavBar title="优惠记录" />

    <div class="page__body">
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

        <!--
          一条记录一张卡，不是一张长列表。
          每一条要读三样东西（是什么、多少钱、到账没有）再自己下判断，
          挤在一条条分隔线之间容易看串行。
        -->
        <ul class="cards">
          <li v-for="item in records.data.value ?? []" :key="item.recordId" class="card">
            <span class="card__icon" :class="`card__icon--${item.status.toLowerCase()}`">
              <Icon name="gift" :size="20" />
            </span>

            <div class="card__main">
              <p class="card__title">{{ item.title }}</p>
              <p class="card__time">{{ item.createTime }}</p>
            </div>

            <div class="card__side">
              <!-- 面值可能带小数（现金红包），走 money 工具；实物类没有面值 -->
              <span v-if="item.amount !== null" class="card__amount">
                +{{ formatWithSeparator(money(item.amount)) }}
              </span>
              <span class="card__status" :class="`card__status--${item.status.toLowerCase()}`">
                {{ item.statusText }}
              </span>
            </div>
          </li>
        </ul>
      </Section>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* 🔴 左右内边距。没有它，卡片会直接贴着屏幕边缘 */
.page__body {
  flex: 1;
  padding: var(--sv-space-md) var(--sv-space-page) calc(var(--sv-safe-bottom) + var(--sv-space-lg));
}

.page__count {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
  font-variant-numeric: tabular-nums;
}

.cards {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin: 0;
  padding: 0;
  list-style: none;
}

.card {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

/*
 * 图标底色跟着状态走。它是这一页唯一的色块 ——
 * 用户扫一眼就能看出「哪几条还没到账」，而不用逐行读右边那个状态字。
 */
.card__icon {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
}

.card__icon--done {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.card__main {
  flex: 1;
  min-width: 0;
}

.card__title {
  margin: 0;
  font-size: var(--sv-font-caption);
  font-weight: 500;
  color: var(--sv-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 时间是真实信息，不是占位提示 —— 判据见 SessionsView 的 .card__meta */
.card__time {
  margin: 3px 0 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
  font-variant-numeric: tabular-nums;
}

.card__side {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sv-space-xs);
}

.card__amount {
  font-size: var(--sv-font-body);
  font-weight: 600;
  color: var(--sv-text-primary);
  font-variant-numeric: tabular-nums;
}

/*
 * 状态做成小胶囊而不是一行彩色字：一行彩字在卡片里会和金额抢注意力，
 * 而胶囊有边界，读起来是「一个标签」，不是「另一个数字」。
 */
.card__status {
  padding: 1px 8px;
  border-radius: var(--sv-radius-pill);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
  white-space: nowrap;
}

.card__status--pending {
  background: var(--sv-color-warning-soft);
  color: var(--sv-color-warning);
}

.card__status--done {
  background: var(--sv-color-success-soft);
  color: var(--sv-color-success);
}

.card__status--failed {
  background: var(--sv-color-danger-soft);
  color: var(--sv-color-danger);
}
</style>
