<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/errors'
import {
  createRechargeOrder,
  fetchRechargeOptions,
  fetchRechargeOrders,
  payRechargeOrder,
  trialRechargeCoupons,
  type RechargeCouponItem,
} from '@/api/recharge'
import { useAsync } from '@/composables/useAsync'
import type { Id } from '@/types/contract'

/**
 * 充话费。
 *
 * <h3>它是券的第一个「非商城」出口</h3>
 * 这一页真正要证明的是：券模板里 `scope_type = EXTERNAL` 那一档不是摆设，
 * 一张券能被商城以外的场景消费掉。
 *
 * <h3>⚠️ 今天运营商那一端是假的 —— 而且这一点要写在页面上</h3>
 * 下单、扣券、标成功，**但话费不会到账**。不写出来的话，测试环境里
 * 点完的人会等一条永远不来的到账短信。
 *
 * <p>🔴 场景关着时**如实显示「暂未开放」，不把入口藏起来** ——
 * 藏起来用户会以为是自己的问题。
 *
 * <h3>选券：不可用的也要列出来</h3>
 * 用户手里有券却在这一页看不到它，第一反应是系统坏了，
 * 而真实原因往往只是「没到门槛」。
 */

const options = useAsync(fetchRechargeOptions)
const orders = useAsync(fetchRechargeOrders)

const phone = ref('')
const amount = ref<string | null>(null)

/* ---- 选券 ---- */
const usableCoupons = ref<RechargeCouponItem[]>([])
const unusableCoupons = ref<RechargeCouponItem[]>([])
const chosenCouponId = ref<Id | null>(null)
const pickerOpen = ref(false)

const chosenCoupon = computed(
  () => usableCoupons.value.find((c) => c.couponId === chosenCouponId.value) ?? null,
)

/**
 * 面额一变就重新试算，并把已选的券清掉。
 *
 * 🔴 不清的话，用户从 100 改成 10 之后，原来那张「满 100 减 10」还显示着，
 * 他会带着一张用不了的券去下单，然后被后端拒掉 —— 而页面上那张券好端端的。
 */
watch(amount, async (value) => {
  chosenCouponId.value = null
  pickerOpen.value = false
  usableCoupons.value = []
  unusableCoupons.value = []
  if (value === null) {
    return
  }
  try {
    const result = await trialRechargeCoupons(value)
    usableCoupons.value = result.usable
    unusableCoupons.value = result.unusable
  } catch {
    // 选券挂了不该挡住充值本身 —— 用户还是可以不用券把单下了
    usableCoupons.value = []
    unusableCoupons.value = []
  }
})

const payAmount = computed(() => {
  if (amount.value === null) {
    return '0'
  }
  const discount = chosenCoupon.value === null ? 0 : Number(chosenCoupon.value.discountAmount)
  return String(Math.max(0, Number(amount.value) - discount))
})

/* ---- 提交 ---- */
const submitting = ref(false)
const hintText = ref('')

const blockedReason = computed<string | null>(() => {
  if (options.data.value !== null && !options.data.value.enabled) {
    return '充话费暂未开放'
  }
  if (!/^1\d{10}$/.test(phone.value)) {
    return '请填写 11 位手机号'
  }
  if (amount.value === null) {
    return '请选择充值面额'
  }
  return null
})

async function onSubmit(): Promise<void> {
  if (submitting.value || blockedReason.value !== null || amount.value === null) {
    return
  }
  submitting.value = true
  hintText.value = ''
  try {
    /*
     * 下单 → 立刻支付。今天支付和执行都是假的，所以是两步连着走；
     * 真接了支付之后这里会停在「待支付」，由用户去点第二步。
     */
    const created = await createRechargeOrder({
      targetAccount: phone.value,
      amount: amount.value,
      // 只报「用哪张券」，不报「减多少」—— 后者由服务端重新试算
      couponId: chosenCouponId.value,
    })
    await payRechargeOrder(created.orderNo)
    hintText.value = '充值成功'
    amount.value = null
    await orders.reload()
  } catch (error) {
    hintText.value = error instanceof ApiError ? error.message : '充值失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

function pickCoupon(couponId: Id | null): void {
  chosenCouponId.value = couponId
  pickerOpen.value = false
}
</script>

<template>
  <div class="page">
    <NavBar title="充话费" />

    <div class="page__body">
      <!--
        ⚠️ 假通道必须写在页面上。不写的话，测试环境里点完的人
           会等一条永远不来的到账短信。
      -->
      <p v-if="options.data.value?.enabled" class="banner">
        ⚠️ 当前是<b>演示通道</b>：下单与券都是真的，但话费不会真的到账。
      </p>

      <!-- 🔴 关着也要说，不要把入口藏起来 —— 藏起来用户会以为是自己的问题 -->
      <Section
        title="充值"
        :loading="options.loading.value"
        :error="options.error.value"
        :empty="options.data.value !== null && !options.data.value.enabled"
        empty-text="充话费暂未开放，敬请期待"
        @retry="options.reload"
      >
        <div class="form">
          <input
            v-model="phone"
            class="form__input"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            placeholder="请输入手机号"
          />

          <div class="faces">
            <button
              v-for="face in options.data.value?.faceValues ?? []"
              :key="face"
              type="button"
              class="face"
              :class="{ 'face--on': amount === face }"
              @click="amount = face"
            >
              {{ face }} 元
            </button>
          </div>

          <!-- 选券。不可用的也列出来，置灰 + 一句原因 -->
          <template v-if="amount !== null">
            <button type="button" class="coupon-row" @click="pickerOpen = !pickerOpen">
              <span v-if="chosenCoupon !== null" class="coupon-row__on">
                {{ chosenCoupon.couponName }} · 减 {{ chosenCoupon.discountAmount }} 元
              </span>
              <span v-else-if="usableCoupons.length > 0">有 {{ usableCoupons.length }} 张可用</span>
              <span v-else class="coupon-row__muted">暂无可用券</span>
              <Icon name="chevron" :size="18" />
            </button>

            <ul v-if="pickerOpen" class="picks">
              <li>
                <button type="button" class="pick" @click="pickCoupon(null)">
                  <span>不使用优惠券</span>
                  <Icon v-if="chosenCouponId === null" name="check" :size="16" />
                </button>
              </li>
              <li v-for="item in usableCoupons" :key="item.couponId">
                <button type="button" class="pick" @click="pickCoupon(item.couponId)">
                  <span class="pick__main">
                    <span>{{ item.couponName }}</span>
                    <span class="pick__save">可减 {{ item.discountAmount }} 元</span>
                  </span>
                  <Icon v-if="chosenCouponId === item.couponId" name="check" :size="16" />
                </button>
              </li>
              <li v-for="item in unusableCoupons" :key="item.couponId">
                <span class="pick pick--off">
                  <span class="pick__main">
                    <span>{{ item.couponName }}</span>
                    <span class="pick__why">{{ item.reasonDesc }}</span>
                  </span>
                </span>
              </li>
            </ul>
          </template>

          <div v-if="amount !== null" class="bill">
            <span>实付</span>
            <span class="bill__value">¥{{ payAmount }}</span>
          </div>

          <p v-if="hintText !== ''" class="hint" role="alert">{{ hintText }}</p>
          <p v-else-if="blockedReason !== null" class="hint hint--muted">{{ blockedReason }}</p>

          <Button :disabled="blockedReason !== null" :loading="submitting" @click="onSubmit">
            立即充值
          </Button>
        </div>
      </Section>

      <Section
        title="充值记录"
        :loading="orders.loading.value"
        :error="orders.error.value"
        :empty="(orders.data.value ?? []).length === 0"
        empty-text="还没有充值记录"
        @retry="orders.reload"
      >
        <ul class="orders">
          <li v-for="order in orders.data.value ?? []" :key="order.orderNo" class="order">
            <div class="order__main">
              <p class="order__title">{{ order.targetMasked }} · {{ order.originalAmount }} 元</p>
              <p class="order__meta">
                <span v-if="order.couponDiscount">券减 {{ order.couponDiscount }} 元 · </span>
                实付 ¥{{ order.payAmount }}
              </p>
              <p class="order__time">{{ order.createTime }}</p>
            </div>
            <span class="order__status">{{ order.statusDesc }}</span>
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

.banner {
  margin: 0 0 var(--sv-space-md);
  padding: var(--sv-space-sm) var(--sv-space-md);
  border-radius: var(--sv-radius-md);
  background: var(--sv-color-warning-soft);
  color: var(--sv-color-warning);
  font-size: var(--sv-font-footnote);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
}

.form__input {
  width: 100%;
  height: var(--sv-field-height);
  padding: 0 var(--sv-space-md);
  border: 1px solid var(--sv-border-color);
  border-radius: var(--sv-radius-field);
  background: var(--sv-bg-surface);
  color: var(--sv-text-primary);
  font-size: var(--sv-font-body);
}

.faces {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sv-space-sm);
}

.face {
  padding: var(--sv-space-md) 0;
  border: 1px solid var(--sv-border-color);
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-surface);
  color: var(--sv-text-primary);
  font-size: var(--sv-font-caption);
  cursor: pointer;
}

.face--on {
  border-color: var(--sv-color-primary);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-weight: 600;
}

.coupon-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: var(--sv-space-md);
  border: 0;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-surface);
  font-size: var(--sv-font-caption);
  color: var(--sv-text-primary);
  cursor: pointer;
}

.coupon-row__on {
  color: var(--sv-color-primary);
  font-weight: 500;
}

.coupon-row__muted {
  color: var(--sv-text-placeholder);
}

.picks {
  margin: 0;
  padding: 0;
  list-style: none;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-surface);
  overflow: hidden;
}

.pick {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: var(--sv-space-md);
  border: 0;
  border-top: 1px solid var(--sv-border-color);
  background: none;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-primary);
  text-align: left;
  cursor: pointer;
}

.picks > li:first-child .pick {
  border-top: 0;
}

/* 不可用的券压暗且不可点，但【留在列表里】—— 见脚本里那段说明 */
.pick--off {
  opacity: 0.55;
  cursor: default;
}

.pick__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.pick__save {
  font-size: var(--sv-font-footnote);
  color: var(--sv-color-primary);
}

.pick__why {
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
}

.bill {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-secondary);
}

.bill__value {
  font-size: var(--sv-font-title);
  font-weight: 600;
  color: var(--sv-text-primary);
  font-variant-numeric: tabular-nums;
}

.hint {
  margin: 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-color-danger);
}

.hint--muted {
  color: var(--sv-text-secondary);
}

.orders {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin: 0;
  padding: 0;
  list-style: none;
}

.order {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.order__main {
  flex: 1;
  min-width: 0;
}

.order__title {
  margin: 0;
  font-size: var(--sv-font-caption);
  font-weight: 500;
  color: var(--sv-text-primary);
}

.order__meta {
  margin: 4px 0 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-primary);
}

.order__time {
  margin: 3px 0 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
  font-variant-numeric: tabular-nums;
}

.order__status {
  flex: none;
  padding: 1px 8px;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
  white-space: nowrap;
}
</style>
