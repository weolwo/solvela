<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()

const loggingOut = ref(false)

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

    <SvCard>
      <SvCell icon="settings" title="设置" :to="{ name: 'settings' }" />
      <!--
        主题只有一套皮肤，所以是只读行：右边显示当前值，不给箭头。
        箭头意味着「可以点进去」，而现在点进去没有第二个选项可选。
        第二套皮肤落地后把 arrow 打开、加上 to 即可
      -->
      <SvCell icon="palette" title="主题" :value="theme.label" :arrow="false" />
    </SvCard>

    <SvCard>
      <SvCell icon="logout" title="退出登录" danger :arrow="false" @click="handleLogout" />
    </SvCard>

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

.page__version {
  margin: var(--sv-space-md) 0 0;
  text-align: center;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}
</style>
