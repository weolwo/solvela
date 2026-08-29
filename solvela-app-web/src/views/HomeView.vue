<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { formatWithSeparator, money } from '@/utils/money'

const router = useRouter()
const auth = useAuthStore()

// 演示：金额从后端来是字符串，展示要走 money 工具，不能 Number() 之后 toFixed
const demoBalance = computed(() => formatWithSeparator(money('12345.678')))

async function handleLogout(): Promise<void> {
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="home">
    <div class="home__card">
      <p class="home__label">当前会员</p>
      <p class="home__value">{{ auth.member?.nickname ?? '—' }}</p>
      <!-- memberId 是 Id（字符串），直接渲染即可，不要 parseInt 也不要参与计算 -->
      <p class="home__muted">ID {{ auth.member?.memberId ?? '—' }}</p>
    </div>

    <div class="home__card">
      <p class="home__label">余额（示例）</p>
      <p class="home__amount">¥{{ demoBalance }}</p>
    </div>

    <var-button type="default" block @click="handleLogout">退出登录</var-button>
  </div>
</template>

<style scoped>
.home {
  padding: var(--sv-space-md);
  padding-top: calc(var(--sv-safe-top) + var(--sv-space-md));
}

.home__card {
  margin-bottom: var(--sv-space-md);
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.home__label {
  margin: 0 0 var(--sv-space-xs);
  color: var(--sv-text-secondary);
  font-size: 13px;
}

.home__value {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.home__muted {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-placeholder);
  font-size: 12px;
}

.home__amount {
  margin: 0;
  color: var(--sv-color-primary);
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
</style>
