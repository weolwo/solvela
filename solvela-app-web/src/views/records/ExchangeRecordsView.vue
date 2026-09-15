<script setup lang="ts">
import { ref } from 'vue'

import { ApiError } from '@/api/errors'
import { payOrder } from '@/api/mall'
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
 *
 * <h3>🔴 2026-09-10 重做了视觉，修的是三个叠在一起的问题</h3>
 * <ul>
 *   <li><b>整页没有左右内边距</b> —— 卡片直接贴着屏幕边缘；</li>
 *   <li><b>Card 不自带 padding</b>（本项目的约定是由子元素给，见 Cell），
 *       而这里直接把内容塞进 Card，于是图片和文字紧贴卡片边框；</li>
 *   <li>辅助文字用了<b>不存在的</b> `--sv-text-tertiary`，取不到值就继承父级 ——
 *       本该是浅灰的单号和时间渲染成了正文深色。现在由
 *       `styles/__tests__/tokens.spec.ts` 盯着。</li>
 * </ul>
 */

const orders = useAsync(fetchExchangeRecords)

/**
 * 「去支付」。
 *
 * ⚠️ 后端今天是**假支付**：点一下就算付了，不动任何真钱。
 * 前端这一侧不做任何环境判断 —— 那道闸在后端（配到生产会启动失败），
 * 判断散在两处的话总有一处会忘。
 *
 * 🔴 付完必须**重拉整页**，不能本地把这一单改成「待履约」：
 * 这一单可能刚好被超时 job 取消了、库存也可能变了，本地猜一个状态
 * 只会让用户看到一个和服务端不一致的页面，而他会按那个去做下一步。
 */
const paying = ref<string | null>(null)
const payError = ref('')

async function onPay(orderNo: string): Promise<void> {
  if (paying.value !== null) {
    return
  }
  paying.value = orderNo
  payError.value = ''
  try {
    await payOrder(orderNo)
    await orders.reload()
  } catch (error) {
    // 「这单已经不能支付了」是预期内的（超时取消 / 已付过），按人话提示
    payError.value = error instanceof ApiError ? error.message : '支付失败，请稍后再试'
  } finally {
    paying.value = null
  }
}
</script>

<template>
  <div class="page">
    <NavBar title="兑换记录" />

    <div class="page__body">
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

          <!--
            待支付才画这个按钮，而「能不能支付」是【服务端】给的 payable ——
            前端按 status 自己推的话，状态机改一次就会有一个端开始给出错的按钮。
          -->
          <div v-if="order.payable" class="order__pay">
            <p v-if="payError !== '' && paying === null" class="order__pay-error" role="alert">
              {{ payError }}
            </p>
            <Button
              size="small"
              :loading="paying === order.orderNo"
              :disabled="paying !== null"
              @click="onPay(order.orderNo)"
            >
              去支付
            </Button>
          </div>
        </Card>
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
  font-size: var(--sv-font-caption);
  color: var(--sv-text-placeholder);
  font-variant-numeric: tabular-nums;
}

/*
 * 🔴 内边距由这里给，不是由 Card 给。
 * Card 只提供「白底 + 圆角」这层皮，内容自己留白 —— 与 Cell 同一个约定。
 * 不给的话，图片和文字会紧贴卡片边框。
 */
.order {
  padding: var(--sv-space-md);
}

.order + .order {
  margin-top: var(--sv-space-sm);
}

.order__head {
  display: flex;
  gap: var(--sv-space-md);
  align-items: flex-start;
}

.order__img {
  flex: none;
  width: 56px;
  height: 56px;
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
  color: var(--sv-text-placeholder);
}

.order__main {
  flex: 1;
  min-width: 0;
}

.order__title {
  margin: 0;
  font-size: var(--sv-font-caption);
  font-weight: 600;
  color: var(--sv-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order__specs,
.order__cost {
  margin: var(--sv-space-xs) 0 0;
  font-size: var(--sv-font-footnote);
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
  gap: var(--sv-space-xs);
}

/*
 * 状态做成小胶囊而不是一行彩色字 —— 与优惠记录同一个处理。
 * 一行彩字在卡片里会和数量抢注意力，而胶囊读起来是「一个标签」。
 */
.order__pay {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-sm);
}

.order__pay-error {
  flex: 1;
  margin: 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-color-danger);
}

.order__status {
  padding: 1px 8px;
  border-radius: var(--sv-radius-pill);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
  white-space: nowrap;
}

.order__status--pending {
  background: var(--sv-color-warning-soft);
  color: var(--sv-color-warning);
}

.order__status--done {
  background: var(--sv-color-success-soft);
  color: var(--sv-color-success);
}

.order__status--failed {
  background: var(--sv-color-danger-soft);
  color: var(--sv-color-danger);
}

/* 件数、单号、时间都是真实信息 —— 判据见 SessionsView 的 .card__meta */
.order__qty,
.order__no,
.order__time {
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
  font-variant-numeric: tabular-nums;
}

.order__hint {
  margin: var(--sv-space-sm) 0 0;
  padding: var(--sv-space-sm) var(--sv-space-md);
  font-size: var(--sv-font-footnote);
  line-height: 1.5;
  color: var(--sv-text-secondary);
  background: var(--sv-bg-fill);
  border-radius: var(--sv-radius-sm);
}

/* 单号和时间是「查完就走」的信息，用一条细线和上面的主体分开 */
.order__foot {
  display: flex;
  justify-content: space-between;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-sm);
  padding-top: var(--sv-space-sm);
  border-top: 1px solid var(--sv-border-color);
}
</style>
