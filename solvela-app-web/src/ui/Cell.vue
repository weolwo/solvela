<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'

import type { IconName } from './Icon.vue'

/**
 * 设置类列表的一行：左图标 + 标题 +（可选）右侧值 + 箭头。
 *
 * <p>渲染成什么标签由用途决定，不是风格选择：
 * 有 `to` 就是 `<RouterLink>`，纯点击就是 `<button>`，都没有才是 `<div>`。
 * 全用 div 加 `@click` 的话，键盘到不了、读屏不报「按钮」或「链接」——
 * 而这三种语义浏览器本来免费给。
 */

withDefaults(
  defineProps<{
    icon: IconName
    title: string
    /** 右侧的当前值，如主题名 */
    value?: string | undefined
    /**
     * 传了就渲染成路由链接。用命名路由对象（`{ name: 'settings' }`）而不是
     * 拼路径字符串 —— 路径改名时命名路由自动跟着走，字符串拼出来的会静默失效
     */
    to?: RouteLocationRaw | undefined
    /** 显示右箭头。纯展示行（如「版本号」）应当关掉 —— 箭头意味着可以点进去 */
    arrow?: boolean
    /** 危险操作，如退出登录 */
    danger?: boolean
  }>(),
  { arrow: true, danger: false },
)
</script>

<template>
  <component
    :is="to !== undefined ? 'RouterLink' : 'button'"
    class="sv-cell"
    :class="{ 'sv-cell--danger': danger }"
    :to="to"
    :type="to === undefined ? 'button' : undefined"
  >
    <Icon :name="icon" :size="20" class="sv-cell__icon" />
    <span class="sv-cell__title">{{ title }}</span>
    <span v-if="value !== undefined" class="sv-cell__value">{{ value }}</span>
    <Icon v-if="arrow" name="chevron" :size="18" class="sv-cell__arrow" />
  </component>
</template>

<style scoped>
.sv-cell {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  width: 100%;
  min-height: var(--sv-row-height);
  padding: 0 var(--sv-space-md);
  border: 0;
  background: transparent;
  color: var(--sv-text-primary);
  font: inherit;
  font-size: var(--sv-font-body);
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}

.sv-cell:active {
  background: var(--sv-bg-pressed);
}

.sv-cell:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: -2px;
}

.sv-cell__icon {
  color: var(--sv-text-secondary);
}

.sv-cell__title {
  flex: 1;
  min-width: 0;
}

.sv-cell__value {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.sv-cell__arrow {
  color: var(--sv-text-placeholder);
}

.sv-cell--danger,
.sv-cell--danger .sv-cell__icon {
  color: var(--sv-color-danger);
}
</style>
