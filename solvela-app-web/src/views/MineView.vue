<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { fetchAssets } from '@/api/assets'
import { fetchRecords } from '@/api/records'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { formatWithSeparator, money } from '@/utils/money'

const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()

const loggingOut = ref(false)

/*
 * 资产与订单记录原来在首页。首页改成商城/任务/活动三个 tab 之后它们搬到这里 ——
 * 语义上本来就更对：这些是「我的」东西，不是逛的东西。
 */
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

/** 没有头像时用昵称首字兜底，比一个通用灰人像更有辨识度 */
const initial = computed(() => auth.member?.nickname?.trim().charAt(0) ?? '?')

/**
 * 头像 URL。
 *
 * ⚠️ 后端给的是 `avatarFileId`，不是 URL —— 文件访问要走 solvela-base-file 的
 * 下载接口，而网关目前没有暴露它。所以现在恒为 null，一律走首字兜底。
 * 接上之后这里改成拼下载地址即可，模板不用动。
 */
const avatarUrl = computed<string | null>(() => null)

async function handleLogout(): Promise<void> {
  if (loggingOut.value) {
    return
  }
  loggingOut.value = true
  try {
    // logout 内部已经吞掉了网络异常：令牌本地清掉才是关键，
    // 服务端那次注销失败也不该把用户卡在已登录状态
    await auth.logout()
    await router.replace({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="page">
    <!-- 头像区。用户要的「最上面头像」就是这一块 -->
    <header class="profile">
      <div class="profile__avatar">
        <img v-if="avatarUrl !== null" :src="avatarUrl" alt="" class="profile__img" />
        <span v-else aria-hidden="true">{{ initial }}</span>
      </div>
      <div class="profile__text">
        <h1 class="profile__name">{{ auth.member?.nickname ?? '未登录' }}</h1>
        <!--
          🔴 这里显示的是会员号，不是手机号。
          后端 MemberPrincipal 刻意不带手机号 —— 那个对象会进 Redis、进日志，
          放明文手机号会让整套 PII 加密失效。要展示手机号得走单独接口拿脱敏值。
        -->
        <p class="profile__id">ID {{ auth.member?.memberId ?? '—' }}</p>
      </div>
    </header>

    <!-- 资产卡：整页最重的一块，用主色实底把它和下面的白卡分开 -->
    <div class="wallet">
      <p class="wallet__label">
        {{ primaryAsset?.label ?? '资产' }}
        <!--
          冻结要标出来，而不是把这一项藏起来 —— 藏起来用户会以为资产没了，然后来问客服。
          既有文字也有底色：只靠颜色区分对色觉障碍用户等于没区分。
        -->
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

    <Section
      title="订单记录"
      :loading="records.loading.value"
      :error="records.error.value"
      :empty="(records.data.value ?? []).length === 0"
      empty-text="还没有记录，去商城或活动中心看看"
      @retry="records.reload"
    >
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

    <Card>
      <Cell icon="heart" title="我的收藏" :to="{ name: 'favorites' }" />
      <Cell icon="home" title="地址簿" :to="{ name: 'address-list' }" />
      <Cell icon="settings" title="设置" :to="{ name: 'settings' }" />
      <!--
        主题只有一套皮肤，所以是只读行：右边显示当前值，不给箭头。
        箭头意味着「可以点进去」，而现在点进去没有第二个选项可选。
        第二套皮肤落地后把 arrow 打开、加上 to 即可
      -->
      <Cell icon="palette" title="主题" :value="theme.label" :arrow="false" />
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
  padding: var(--sv-space-xs) var(--sv-space-xs) var(--sv-space-md);
}

.profile__avatar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
  font-size: 26px;
  font-weight: 600;
}

.profile__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile__text {
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

.profile__id {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
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

.page__version {
  margin: var(--sv-space-md) 0 0;
  text-align: center;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}
</style>
