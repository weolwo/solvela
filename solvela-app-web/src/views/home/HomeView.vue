<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

/**
 * 首页、商城、任务、活动中心四个页面共用的外壳：只负责页头。
 *
 * <h3>2026-09-27 去掉了顶部 tab</h3>
 * 此前这里有一排「首页 / 商城 / 任务中心 / 活动中心」小 tab，而底部导航只有「首页 / 我的」——
 * 每天都要做的签到、逛商城被压在二级位置。现在商城和任务升到了底部导航（见 TabBar），
 * 活动中心从首页的「全部活动」进。顶部再留一排 tab 就是同一组入口画两遍。
 *
 * <h3>为什么还保留外壳和子路由</h3>
 * 四条路径（`/`、`/mall`、`/tasks`、`/activities`）早已被分享、收藏出去，也是任务 actionUrl
 * 里配的值 —— 路径一个都不改。外壳留着，页头这一处的样式就只有一份。
 */

const auth = useAuthStore()
const route = useRoute()

/** 首页自己是问候语；其余三页是标题（取路由 meta.title，不在这里再写一份） */
const isFeed = computed(() => route.name === 'feed')
const title = computed(() => route.meta.title ?? '')
</script>

<template>
  <div class="home">
    <header class="home__head">
      <div v-if="isFeed" class="home__greet">
        <p class="home__hello">你好，{{ auth.member?.nickname ?? '朋友' }} 👋</p>
        <p class="home__sub">今天想看点什么</p>
      </div>
      <h1 v-else class="home__title">{{ title }}</h1>
    </header>

    <RouterView v-slot="{ Component }">
      <component :is="Component" />
    </RouterView>
  </div>
</template>

<style scoped>
.home {
  padding-bottom: var(--sv-space-lg);
}

.home__head {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: calc(var(--sv-safe-top) + var(--sv-space-lg)) var(--sv-space-page) var(--sv-space-md);
}

.home__greet {
  flex: 1;
  min-width: 0;
}

.home__hello {
  margin: 0;
  font-size: var(--sv-font-title);
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home__sub {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.home__title {
  margin: 0;
  font-size: var(--sv-font-title);
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.2;
}
</style>
