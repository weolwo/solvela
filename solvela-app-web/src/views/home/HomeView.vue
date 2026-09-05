<script setup lang="ts">
import { RouterView } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

/**
 * 首页外壳：问候语 + 搜索入口 + 三个顶部 tab（商城 / 任务中心 / 活动中心）。
 *
 * <h3>tab 为什么是路由，不是一个 ref</h3>
 * 四个 tab 是 `/`、`/mall`、`/tasks`、`/activities` 四条<b>子路由</b>，不是本组件里的
 * `activeTab` 状态。这样换来三件事：
 * <ul>
 *   <li>返回键能在 tab 之间退，用户按返回不会一下子退出首页；</li>
 *   <li>能直接分享/深链到某个 tab，底部导航去掉的「优惠」也才能
 *       用一条 redirect 指到活动中心；</li>
 *   <li>四个 pane 各自懒加载 —— 只逛商城的用户不会下载任务中心的代码。</li>
 * </ul>
 *
 * <h3>为什么没有截图里那个汉堡菜单</h3>
 * 这个 app 没有侧边抽屉，也没有别的东西要塞进去。画一个点了没反应的图标
 * 比不画更糟 —— 用户会去点它。等真有抽屉了再加。
 */

const auth = useAuthStore()

const TABS = [
  { name: 'feed', label: '首页' },
  { name: 'mall', label: '商城' },
  { name: 'tasks', label: '任务中心' },
  { name: 'activities', label: '活动中心' },
] as const
</script>

<template>
  <div class="home">
    <header class="home__head">
      <div class="home__greet">
        <p class="home__hello">你好，{{ auth.member?.nickname ?? '朋友' }} 👋</p>
        <p class="home__sub">今天想看点什么</p>
      </div>
    </header>

    <!--
      顶部 tab 吸顶：往下滚商品时还能换 tab。用 <nav> + RouterLink 而不是按钮，
      理由和底部 TabBar 一样 —— 能长按复制、能被读屏报成链接、中键能开新标签。
    -->
    <nav class="tabs" aria-label="首页分区">
      <RouterLink
        v-for="tab in TABS"
        :key="tab.name"
        class="tabs__item"
        active-class="tabs__item--active"
        :to="{ name: tab.name }"
      >
        {{ tab.label }}
      </RouterLink>
    </nav>

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

.tabs {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  gap: var(--sv-space-lg);
  padding: var(--sv-space-sm) var(--sv-space-page);
  background: var(--sv-bg-page);
  /* 四个 tab 在窄屏上放不下，横滚而不是换行 —— 换行会把内容整体顶下去一行 */
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tabs__item {
  position: relative;
  flex: none;
  padding: var(--sv-space-xs) 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-body);
  text-decoration: none;
  /* 选中时字重变粗会让整条 tab 抖一下；预留最粗那一档的宽度 */
  transition: color 0.15s ease;
}

.tabs__item--active {
  color: var(--sv-text-primary);
  font-weight: 700;
}

/* 下划线用伪元素，不占布局，也就不会在切换时推动旁边的 tab */
.tabs__item--active::after {
  content: '';
  position: absolute;
  inset: auto 0 0;
  height: 3px;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary);
}

.tabs__item:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
  border-radius: var(--sv-radius-sm);
}
</style>
