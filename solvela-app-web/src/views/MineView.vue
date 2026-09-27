<script setup lang="ts">
import { computed, ref } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { useRouter } from 'vue-router'

import { fetchAssets } from '@/api/assets'
import { fetchMeSummary } from '@/api/me'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import type { IconName } from '@/ui/Icon.vue'
import { formatWithSeparator, money } from '@/utils/money'

/**
 * 「我的」。
 *
 * <h3>2026-09-27 重排：从一条 12 行的长列表，改成「一眼看到自己有什么」</h3>
 * 此前券包、彩票、实物奖品、兑换记录、充话费、会员中心、收藏、地址簿……全是同一种样式的行，
 * 用户只能从头扫到尾，而最常问的「我有几张券、奖品寄到哪了」埋在中间。现在分四块：
 * <ol>
 *   <li>页头：等级徽章（→ 会员中心）、铃铛（→ 消息，带未读数）—— 它们是「我是谁」「有人找我」，不是列表项；</li>
 *   <li>资产：钱包卡 + 一排数字（券包 / 彩票 / 奖品 / 收藏），数字本身就是信息；</li>
 *   <li>我的奖品：按「要不要我动手」分组，像电商的「我的订单」；</li>
 *   <li>常用服务宫格（一行四个）+ 设置。</li>
 * </ol>
 *
 * <h3>数字一次从 `/me/summary` 拿齐</h3>
 * 第一版是各拉各的列表再数：多 5 个请求，而且数错 —— 实物单、彩票的列表有条数上限。
 * 现在每个数由各自的域用 COUNT 口径给，网关拼好一次下发（见 api/me.ts）。
 * 任何一个数拿不到都只显示「—」，<b>入口本身永远在</b>：为一次接口抖动把入口藏掉，
 * 是拿次要目标伤害主要目标（消息入口当初就是这么定的）。
 */

const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()

const loggingOut = ref(false)

const assets = useAsync(fetchAssets)
const summary = useAsync(fetchMeSummary)

/**
 * 消息未读数。🔴 服务端把通知和公告两个数加好再下发，端上不要自己拉两个相加 ——
 * 那样迟早漏掉公告那一半。拿不到就不画红点，铃铛照样在。
 */
const unreadCount = computed(() => summary.data.value?.unread ?? 0)
const unreadBadge = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

/** 等级徽章。拿不到时显示「会员中心」—— 入口不能因为名字没拿到就消失 */
const gradeLabel = computed(() => summary.data.value?.gradeName ?? '会员中心')

/** 加载中、出错、这一项为 null 都显示「—」：0 是一个确定的答案，不知道的时候不能说 0 */
function show(n: number | null | undefined): string {
  return n === null || n === undefined ? '—' : String(n)
}

interface Stat {
  label: string
  value: string
  to: RouteLocationRaw
}

const stats = computed<Stat[]>(() => {
  const s = summary.data.value
  return [
    { label: '券包', value: show(s?.coupons), to: { name: 'coupons' } },
    { label: '彩票', value: show(s?.lotteryTickets), to: { name: 'lottery-tickets' } },
    { label: '奖品', value: show(s?.deliveries?.total), to: { name: 'deliveries' } },
    { label: '收藏', value: show(s?.favorites), to: { name: 'favorites' } },
  ]
})

interface DeliveryGroup {
  label: string
  icon: IconName
  /** 数量；不知道时为 null */
  count: number | null
  /** 要用户动手的那一组，有数时要显眼 */
  alert: boolean
}

/**
 * 实物奖品按「用户关心的那件事」分三组。分组口径由资产域给（DeliverySummaryView），
 * 前端不按 status 推 —— 状态机改一次，各端各推一份的那个端就会开始数错。
 */
const deliveryGroups = computed<DeliveryGroup[]>(() => {
  const d = summary.data.value?.deliveries ?? null
  return [
    { label: '待填地址', icon: 'pin', count: d?.needAddress ?? null, alert: true },
    { label: '待发货', icon: 'box', count: d?.pending ?? null, alert: false },
    { label: '已发货', icon: 'truck', count: d?.shipped ?? null, alert: false },
  ]
})

interface Service {
  label: string
  icon: IconName
  to: RouteLocationRaw
}

/**
 * 常用服务，按「多久用一次」排。刻意凑满一行四个：第五个会孤零零掉到第二行。
 * 会员中心不在这里 —— 页头的等级徽章就是它的入口（截图上看得很清楚，不需要第二个）。
 * 地址簿用 home 而不是 pin：pin 已经给了上面的「待填地址」，同屏两个一样的图标等于没有图标。
 */
const SERVICES: Service[] = [
  { label: '充话费', icon: 'phone', to: { name: 'recharge' } },
  { label: '兑换记录', icon: 'clock', to: { name: 'records-exchange' } },
  { label: '优惠记录', icon: 'gift', to: { name: 'records-promo' } },
  { label: '地址簿', icon: 'home', to: { name: 'address-list' } },
]

/**
 * 主资产（列表第一项）单独放大展示。哪一项是主资产由**后端的顺序**决定，
 * 前端不硬编码「SCORE 是主的」—— 那是业务配置，会变。
 */
const primaryAsset = computed(() => assets.data.value?.[0] ?? null)
const otherAssets = computed(() => assets.data.value?.slice(1) ?? [])

/**
 * 金额从后端来是字符串，展示要走 money 工具。
 * 🔴 不要 Number() 之后 toFixed —— 那会在超过 2^53-1 时静默丢精度。
 */
function display(amount: string, currency: boolean): string {
  return currency ? formatWithSeparator(money(amount)) : amount
}

/** 没有头像时用昵称首字兜底，比一个通用灰人像更有辨识度 */
const initial = computed(() => auth.member?.nickname?.trim().charAt(0) ?? '?')

/**
 * 头像 URL。⚠️ 后端给的是 `avatarFileId`，网关目前没有暴露文件下载接口，
 * 所以恒为 null，一律走首字兜底。
 */
const avatarUrl = computed<string | null>(() => null)

async function handleLogout(): Promise<void> {
  if (loggingOut.value) {
    return
  }
  loggingOut.value = true
  try {
    // logout 内部已经吞掉了网络异常：服务端那次注销失败也不该把用户卡在已登录状态
    await auth.logout()
    await router.replace({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="profile">
      <div class="profile__avatar">
        <img v-if="avatarUrl !== null" :src="avatarUrl" alt="" class="profile__img" />
        <span v-else aria-hidden="true">{{ initial }}</span>
      </div>
      <div class="profile__text">
        <h1 class="profile__name">{{ auth.member?.nickname ?? '未登录' }}</h1>
        <div class="profile__meta">
          <!--
            🔴 这里显示的是会员号，不是手机号。MemberPrincipal 刻意不带手机号 ——
            那个对象会进 Redis、进日志，放明文手机号会让整套 PII 加密失效。
          -->
          <span class="profile__id">ID {{ auth.member?.memberId ?? '—' }}</span>
          <!-- 等级是「我是谁」，放在名字旁边，而不是列表里的一行 -->
          <RouterLink class="profile__grade" :to="{ name: 'grade' }">
            <Icon name="crown" :size="14" />
            {{ gradeLabel }}
          </RouterLink>
        </div>
      </div>
      <!-- 消息：通用的铃铛 + 红点，不再是列表里一行（此前用的还是礼物图标） -->
      <RouterLink
        class="profile__bell"
        :to="{ name: 'messages' }"
        :aria-label="unreadCount > 0 ? `消息，${unreadCount} 条未读` : '消息'"
      >
        <Icon name="bell" :size="24" />
        <span v-if="unreadCount > 0" class="profile__badge" aria-hidden="true">
          {{ unreadBadge }}
        </span>
      </RouterLink>
    </header>

    <!-- 资产卡：整页最重的一块，用主色实底把它和下面的卡分开 -->
    <div class="wallet">
      <p class="wallet__label">
        {{ primaryAsset?.label ?? '资产' }}
        <!-- 冻结要标出来，而不是把这一项藏起来 —— 藏起来用户会以为资产没了 -->
        <span v-if="primaryAsset?.frozen === true" class="wallet__frozen">已冻结</span>
      </p>
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
          <span v-if="item.frozen" class="wallet__frozen">已冻结</span>
        </div>
      </div>
    </div>

    <!-- 我有什么：数字本身就是信息，不用点进去才知道有没有 -->
    <nav class="stats" aria-label="我的资产">
      <RouterLink v-for="s in stats" :key="s.label" class="stats__item" :to="s.to">
        <span class="stats__value">{{ s.value }}</span>
        <span class="stats__label">{{ s.label }}</span>
      </RouterLink>
    </nav>

    <!-- 我的奖品：像电商的「我的订单」，按要不要我动手分组 -->
    <section class="block" aria-labelledby="mine-prizes">
      <div class="block__head">
        <h2 id="mine-prizes" class="block__title">我的奖品</h2>
        <RouterLink class="block__more" :to="{ name: 'deliveries' }">
          全部 <Icon name="chevron" :size="14" />
        </RouterLink>
      </div>
      <div class="grid grid--3">
        <RouterLink
          v-for="g in deliveryGroups"
          :key="g.label"
          class="grid__item"
          :class="{ 'grid__item--alert': g.alert && (g.count ?? 0) > 0 }"
          :to="{ name: 'deliveries' }"
        >
          <span class="grid__icon">
            <Icon :name="g.icon" :size="24" />
            <span v-if="(g.count ?? 0) > 0" class="grid__count">{{ g.count }}</span>
          </span>
          <span class="grid__label">{{ g.label }}</span>
        </RouterLink>
      </div>
    </section>

    <section class="block" aria-labelledby="mine-services">
      <div class="block__head">
        <h2 id="mine-services" class="block__title">常用服务</h2>
      </div>
      <div class="grid grid--4">
        <RouterLink v-for="s in SERVICES" :key="s.label" class="grid__item" :to="s.to">
          <span class="grid__icon"><Icon :name="s.icon" :size="24" /></span>
          <span class="grid__label">{{ s.label }}</span>
        </RouterLink>
      </div>
    </section>

    <Card>
      <Cell icon="settings" title="设置" :to="{ name: 'settings' }" />
      <Cell icon="palette" title="主题" :value="theme.label" :to="{ name: 'theme' }" />
    </Card>

    <Card>
      <Cell icon="logout" title="退出登录" danger :arrow="false" @click="handleLogout" />
    </Card>

    <p class="page__version">Solvela · 开发版</p>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding: calc(var(--sv-safe-top) + var(--sv-space-lg)) var(--sv-space-page) var(--sv-space-lg);
}

.profile {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-xs) var(--sv-space-xs) var(--sv-space-sm);
}

.profile__avatar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
  font-size: 24px;
  font-weight: 600;
}

.profile__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile__text {
  flex: 1;
  min-width: 0;
}

.profile__name {
  margin: 0;
  font-size: var(--sv-font-heading);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-xs);
}

.profile__id {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}

.profile__grade {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px var(--sv-space-sm);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-size: var(--sv-font-footnote);
  font-weight: 600;
  text-decoration: none;
}

.profile__bell {
  position: relative;
  flex: none;
  display: flex;
  padding: var(--sv-space-sm);
  color: var(--sv-text-primary);
}

.profile__badge {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-danger);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
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

.wallet__frozen {
  margin-left: var(--sv-space-xs);
  padding: 0 var(--sv-space-xs);
  border-radius: var(--sv-radius-sm);
  background: rgb(255 255 255 / 26%);
  font-size: var(--sv-font-footnote);
  font-weight: 600;
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

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: var(--sv-space-md) 0;
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.stats__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: var(--sv-text-primary);
  text-decoration: none;
}

.stats__value {
  font-size: var(--sv-font-heading);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stats__label {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
}

.block {
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sv-space-md);
}

.block__title {
  margin: 0;
  font-size: var(--sv-font-body);
  font-weight: 600;
}

.block__more {
  display: inline-flex;
  align-items: center;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  text-decoration: none;
}

.grid {
  display: grid;
  row-gap: var(--sv-space-md);
}

.grid--3 {
  grid-template-columns: repeat(3, 1fr);
}

.grid--4 {
  grid-template-columns: repeat(4, 1fr);
}

.grid__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-xs);
  color: var(--sv-text-primary);
  text-decoration: none;
}

.grid__icon {
  position: relative;
  display: flex;
  color: var(--sv-text-secondary);
}

/* 「待填地址」有数时变主色：它是唯一一个不动手就收不到东西的待办 */
.grid__item--alert .grid__icon,
.grid__item--alert .grid__label {
  color: var(--sv-color-primary);
}

.grid__count {
  position: absolute;
  top: -6px;
  right: -12px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-danger);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.grid__label {
  font-size: var(--sv-font-footnote);
}

.page__version {
  margin: var(--sv-space-md) 0 0;
  text-align: center;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}
</style>
