<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fetchCoupons, type CouponTab, type MemberCoupon } from '@/api/coupons'
import { useAsync } from '@/composables/useAsync'

/**
 * 我的券包。
 *
 * <h3>🔴 三个 tab，不是一个混合列表加状态角标</h3>
 * 用户来这一页只会带着一个问题：<b>我现在有什么能用的？</b>
 * 混在一起的话，一个攒了几十张过期券的老用户要滚很久才看得到能用的那两张 ——
 * 而「能用的在哪」正是他唯一想知道的事。
 *
 * <h3>⚠️「可用」这个 tab 不等于 status = 0</h3>
 * 服务端还会做两件事：排除<b>正被某一笔订单锁着</b>的券（此刻点不动），
 * 以及按有效期再筛一次（过期收口任务每天才跑一次，中间那段时间里
 * status 还是 0 但券其实已经过期了）。
 *
 * <p>前端<b>不要自己按 status 过滤</b>：那会让同一条规则在两边各存一份，
 * 而它们一定会不一致。
 *
 * <h3>规则那句话是服务端拼好的</h3>
 * 「满 100 积分可用，减 20 积分」由四个字段组合而成，组合规则会变。
 * 前端自己拼的话，C 端和管理端会显示同一张券的不同规则。
 */

const TABS = [
  { value: 'USABLE' as const, label: '可用' },
  { value: 'USED' as const, label: '已使用' },
  { value: 'INVALID' as const, label: '已失效' },
]

const tab = ref<CouponTab>('USABLE')

const coupons = useAsync(() => fetchCoupons(tab.value))

// 切 tab 重新拉。本地缓存三份看着省事，但券的状态随时会被下单那条链路改掉，
// 缓存的表现是「用完回来券还在可用里」
watch(tab, () => {
  void coupons.reload()
})

const list = computed<MemberCoupon[]>(() => coupons.data.value ?? [])

const tabTitle = computed(() => TABS.find((item) => item.value === tab.value)?.label ?? '券')

const emptyText = computed(() => {
  if (tab.value === 'USABLE') {
    return '还没有可用的券，去活动中心看看'
  }
  return tab.value === 'USED' ? '还没有用过券' : '没有失效的券'
})

/** 已失效那一档里「过期」和「作废」要分得开 —— 用户会问「我的券怎么没了」 */
function statusClass(coupon: MemberCoupon): string {
  if (coupon.status === 1) {
    return 'coupon--used'
  }
  return coupon.status === 0 ? 'coupon--usable' : 'coupon--dead'
}
</script>

<template>
  <div class="page">
    <NavBar title="我的券包" />

    <div class="page__body">
      <div class="tabs">
        <Segmented v-model="tab" :options="TABS" />
      </div>

      <Section
        :title="tabTitle"
        :loading="coupons.loading.value"
        :error="coupons.error.value"
        :empty="list.length === 0"
        :empty-text="emptyText"
        @retry="coupons.reload"
      >
        <template #action>
          <span class="page__count">{{ list.length }} 张</span>
        </template>

        <ul class="coupons">
          <li
            v-for="coupon in list"
            :key="coupon.couponId"
            class="coupon"
            :class="statusClass(coupon)"
          >
            <!--
              左边这一竖条是券的「票根」形状。
              做成券的样子而不是普通卡片，是因为用户在别处见过的券都长这样 ——
              形状本身就在说「这是一张能用的东西」。
            -->
            <div class="coupon__stub">
              <span class="coupon__stub-text">券</span>
            </div>

            <div class="coupon__main">
              <p class="coupon__name">{{ coupon.couponName }}</p>
              <!-- 🔴 服务端拼好的人话，前端不重拼 -->
              <p class="coupon__rule">{{ coupon.ruleText }}</p>
              <p class="coupon__meta">
                <span v-if="coupon.validEndTime">{{ coupon.validEndTime }} 到期</span>
                <span v-if="coupon.usedTime">{{ coupon.usedTime }} 使用</span>
              </p>
            </div>

            <div class="coupon__side">
              <span class="coupon__status">{{ coupon.statusDesc }}</span>
              <!--
                已使用的券显示实际减了多少。
                「这张券当时给我省了多少」是用户回头看券包时唯一关心的事，
                而它和券面规则不是一回事（百分比券取决于订单金额）。
              -->
              <span v-if="coupon.discountAmount" class="coupon__saved">
                已抵 {{ coupon.discountAmount }}
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

.tabs {
  margin-bottom: var(--sv-space-md);
}

.page__count {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
  font-variant-numeric: tabular-nums;
}

.coupons {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin: 0;
  padding: 0;
  list-style: none;
}

.coupon {
  display: flex;
  align-items: stretch;
  gap: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  overflow: hidden;
}

.coupon__stub {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
}

.coupon--usable .coupon__stub {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.coupon__stub-text {
  font-size: var(--sv-font-body);
  font-weight: 600;
}

.coupon__main {
  flex: 1;
  min-width: 0;
  padding: var(--sv-space-md) 0;
}

.coupon__name {
  margin: 0;
  font-size: var(--sv-font-caption);
  font-weight: 500;
  color: var(--sv-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 规则要比券名显眼一点：用户认的是「能减多少」，不是券叫什么 */
.coupon__rule {
  margin: 4px 0 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-primary);
}

.coupon__meta {
  display: flex;
  gap: var(--sv-space-sm);
  margin: 4px 0 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
  font-variant-numeric: tabular-nums;
}

.coupon__side {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: var(--sv-space-xs);
  padding: var(--sv-space-md);
}

.coupon__status {
  padding: 1px 8px;
  border-radius: var(--sv-radius-pill);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
  white-space: nowrap;
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
}

.coupon--usable .coupon__status {
  background: var(--sv-color-success-soft);
  color: var(--sv-color-success);
}

.coupon--used .coupon__status {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.coupon__saved {
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
  font-variant-numeric: tabular-nums;
}

/*
 * 失效的券整体压暗，但【不隐藏】——
 * 用户要能翻到「我那张券是过期了还是被作废了」，藏起来他只会去问客服。
 */
.coupon--dead {
  opacity: 0.6;
}
</style>
