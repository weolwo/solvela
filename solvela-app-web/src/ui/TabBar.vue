<script setup lang="ts">
import type { IconName } from './SvIcon.vue'

/**
 * 底部主导航。
 *
 * <p>用 `<RouterLink>` 而不是 `@click="router.push"`：链接能长按复制、能被读屏
 * 报成「链接」、能用中键在新标签打开 —— 换成 div 全都没了，只为省一个 href。
 *
 * <p>高度写进 `--sv-tabbar-height`，页面靠它留出底部空间。
 * 让每个页面自己写一个魔法数的话，改高度要改 N 处，而漏改的表现是内容被压住。
 */

const TABS: { name: string; label: string; icon: IconName }[] = [
  { name: 'home', label: '首页', icon: 'home' },
  { name: 'promo', label: '优惠', icon: 'gift' },
  { name: 'mine', label: '我的', icon: 'user' },
]
</script>

<template>
  <nav class="sv-tabbar" aria-label="主导航">
    <RouterLink
      v-for="tab in TABS"
      :key="tab.name"
      class="sv-tabbar__item"
      active-class="sv-tabbar__item--active"
      :to="{ name: tab.name }"
    >
      <SvIcon :name="tab.icon" :size="24" />
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
