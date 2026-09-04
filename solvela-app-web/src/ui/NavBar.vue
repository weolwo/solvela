<script setup lang="ts">
import { useRouter } from 'vue-router'

/**
 * 二级页的顶部导航条：返回箭头 + 居中标题。
 *
 * <p>只用在<b>不在底部 Tab 里</b>的页面（设置、将来的活动详情、订单详情……）。
 * 一级页（首页/优惠/我的）不需要它 —— 它们靠底部 Tab 切换，没有"返回"的概念。
 *
 * <p>返回用 `router.back()` 而不是写死跳到某个父页面：写死的话，
 * 如果用户是从"我的"点进来的，返回应该回"我的"；将来某天从别处也能进这个页面，
 * 写死的目标就错了。`back()` 永远符合用户的实际路径。
 */

defineProps<{ title: string }>()

const router = useRouter()
</script>

<template>
  <header class="sv-navbar">
    <button class="sv-navbar__back" type="button" aria-label="返回" @click="router.back()">
      <SvIcon name="chevron" :size="22" style="transform: scaleX(-1)" />
    </button>
    <h1 class="sv-navbar__title">{{ title }}</h1>
    <!-- 占位保持标题居中：左边按钮占了空间，右边补一个等宽的空块抵消 -->
    <span class="sv-navbar__spacer" aria-hidden="true"></span>
  </header>
</template>

<style scoped>
.sv-navbar {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
  padding: calc(var(--sv-safe-top) + var(--sv-space-sm)) var(--sv-space-xs) var(--sv-space-sm);
}

.sv-navbar__back {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--sv-text-primary);
  cursor: pointer;
}

.sv-navbar__back:active {
  background: var(--sv-bg-pressed);
}

.sv-navbar__back:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 1px;
}

.sv-navbar__title {
  flex: 1;
  margin: 0;
  font-size: var(--sv-font-body);
  font-weight: 600;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sv-navbar__spacer {
  flex: none;
  width: 36px;
}
</style>
