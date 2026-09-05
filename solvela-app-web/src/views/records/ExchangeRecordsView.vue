<script setup lang="ts">
import { fetchExchangeRecords } from '@/api/records'
import { useAsync } from '@/composables/useAsync'

/**
 * 兑换记录：我花积分换了什么。
 *
 * <h3>为什么和优惠记录分成两页</h3>
 * 兑换是「我主动花积分买的」，优惠是「平台要发给我的」。
 * 两者的状态机（订单 vs 提案）、金额口径（付出 vs 获得）、
 * 用户点进来想知道的事都不一样，合成一页每条只能显示最小公约数。
 *
 * <h3>全部状态都出</h3>
 * 包括已取消和发放失败。只出成功的等于把「我兑的东西呢」藏起来 ——
 * 而那正是用户点进来最想知道的事。
 */

const orders = useAsync(fetchExchangeRecords)
</script>

<template>
  <div class="page">
    <NavBar title="兑换记录" />

    <Section
      title="我兑换的"
      :loading="orders.loading.value"
      :error="orders.error.value"
      :empty="(orders.data.value ?? []).length === 0"
      empty-text="还没有兑换记录，去商城看看"
      @retry="orders.reload"
    >
      <template #action>
        <span class="page__count">{{ (orders.data.value ?? []).length }} 单</span>
      </template>

      <Card v-for="order in orders.data.value ?? []" :key="order.orderNo" class="order">
        <div class="order__head">
          <!-- 图缺失是正常的（没配图或文件已删），画占位块而不是拼一个 URL 去试 -->
          <img
            v-if="order.coverUrl !== null"
            class="order__img"
            :src="order.coverUrl"
            :alt="order.commodityName"
            loading="lazy"
          />
          <span v-else class="order__img order__img--empty" aria-hidden="true">
            {{ order.commodityName.slice(0, 1) }}
          </span>

          <div class="order__main">
            <p class="order__title">{{ order.commodityName }}</p>
            <p v-if="order.specs.length > 0" class="order__specs">
              {{ order.specs.join('、') }}
            </p>
            <p class="order__cost">{{ order.cost }}</p>
          </div>

          <div class="order__side">
            <span class="order__status" :class="`order__status--${order.status.toLowerCase()}`">
              {{ order.statusText }}
            </span>
            <span class="order__qty">×{{ order.quantity }}</span>
          </div>
        </div>

        <!-- 失败时这一行会说清「积分还在不在」—— 用户看到失败第一个念头就是它 -->
        <p v-if="order.hint !== null" class="order__hint">{{ order.hint }}</p>

        <div class="order__foot">
          <span class="order__no">单号 {{ order.orderNo }}</span>
          <span class="order__time">{{ order.createTime }}</span>
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

.order + .order {
  margin-top: 12px;
}

.order__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.order__img {
  flex: none;
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-fill);
}

.order__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 600;
  color: var(--sv-text-tertiary);
}

.order__main {
  flex: 1;
  min-width: 0;
}

.order__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--sv-text-primary);
}

.order__specs,
.order__cost {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--sv-text-secondary);
}

.order__cost {
  /* 对价里有数字对齐的需求（积分带千分位），等宽数字更整齐 */
  font-variant-numeric: tabular-nums;
  color: var(--sv-text-primary);
}

.order__side {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.order__status {
  font-size: 13px;
  font-weight: 600;
}

.order__status--pending {
  color: var(--sv-color-warning);
}

.order__status--done {
  color: var(--sv-color-success);
}

.order__status--failed {
  color: var(--sv-color-danger);
}

.order__qty,
.order__no,
.order__time {
  font-size: 12px;
  color: var(--sv-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.order__hint {
  margin: 10px 0 0;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--sv-text-secondary);
  background: var(--sv-bg-fill);
  border-radius: var(--sv-radius-sm);
}

.order__foot {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
}
</style>
