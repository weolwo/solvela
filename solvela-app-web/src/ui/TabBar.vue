<script setup lang="ts">
import type { IconName } from './Icon.vue'

/**
 * 底部主导航。
 *
 * <p>用 `<RouterLink>` 而不是 `@click="router.push"`：链接能长按复制、能被读屏
 * 报成「链接」、能用中键在新标签打开 —— 换成 div 全都没了，只为省一个 href。
 *
 * <p>高度写进 `--sv-tabbar-height`，页面靠它留出底部空间。
 * 让每个页面自己写一个魔法数的话，改高度要改 N 处，而漏改的表现是内容被压住。
 */

/*
 * 2026-09-27 从两个 tab（首页 / 我的）改成四个。
 *
 * 此前商城、任务、活动是首页顶部的三个小 tab：签到、逛商城这种每天都做的事，
 * 要先进首页再横着找；而底部只有两个入口，其余一切都塞进了「我的」。
 * 商城和任务升上来；活动中心不单独占位 —— 它本来就在首页内容流里（轮播 + 活动卡 + 「全部活动」）。
 *
 * 首页指向 `feed`（首页自身那条子路由），不是外壳 `home`：外壳是四个页面共用的，
 * 指向它的话在商城、任务上「首页」也会一起高亮。
 *
 * 🔴 高亮用 exact-active-class，不是 active-class：Vue Router 把「指向空路径子路由（feed）」
 * 的链接当成指向父级外壳，于是在 /mall 上「首页」照样算 active。四个 tab 下面都没有嵌套子页，
 * 精确匹配正好。（这条是加 TabBar 测试时才发现的，肉眼看截图时两个都亮着也不一定注意得到。）
 *
 * 「优惠」更早以前也是这里的一个 tab，老路径 /promo 在 router 里 redirect 到活动中心。
 */
const TABS: { name: string; label: string; icon: IconName }[] = [
  { name: 'feed', label: '首页', icon: 'home' },
  { name: 'mall', label: '商城', icon: 'bag' },
  { name: 'tasks', label: '任务', icon: 'task' },
  { name: 'mine', label: '我的', icon: 'user' },
]
</script>

<template>
  <nav class="sv-tabbar" aria-label="主导航">
    <RouterLink
      v-for="tab in TABS"
      :key="tab.name"
      class="sv-tabbar__item"
      exact-active-class="sv-tabbar__item--active"
      :to="{ name: tab.name }"
    >
      <Icon :name="tab.icon" :size="24" />
      <span class="sv-tabbar__label">{{ tab.label }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.sv-tabbar {
  position: fixed;
  inset: auto 0 0;
  z-index: 50;
  display: flex;
  /* 安全区加在 padding 上而不是 height 上：横屏和非刘海屏时它是 0，高度自动回落 */
  padding-bottom: var(--sv-safe-bottom);
  background: var(--sv-bg-surface);
  box-shadow: 0 -0.5px 0 var(--sv-border-color);
}

.sv-tabbar__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: var(--sv-tabbar-height);
  color: var(--sv-text-placeholder);
  text-decoration: none;
  transition: color 0.15s ease;
}

.sv-tabbar__item--active {
  color: var(--sv-color-primary);
}

.sv-tabbar__item:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: -2px;
}

.sv-tabbar__label {
  font-size: 10px;
  line-height: 1.4;
}
</style>
