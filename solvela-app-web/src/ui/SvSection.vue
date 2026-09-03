<script setup lang="ts">
/**
 * 一段内容区：标题 + 加载 / 出错 / 空 / 有数据四种状态。
 *
 * <h3>四种状态一起给，是为了不漏</h3>
 * 手写的话最常漏掉的是「出错」—— 请求挂了页面就永远停在骨架屏上，
 * 而这种 bug 只在后端不可用时才出现，本地开发碰不到。
 * 把四种状态收进一个组件，漏掉的唯一方式是不用它。
 */

defineProps<{
  title: string
  loading: boolean
  error: string | null
  /** 数据为空（不是出错，是确实没有） */
  empty: boolean
  emptyText: string
}>()

defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="sv-section">
    <div class="sv-section__head">
      <h2 class="sv-section__title">{{ title }}</h2>
      <slot name="action" />
    </div>

    <!-- 骨架屏而不是转圈：它把「马上会出现什么形状」提前告诉了用户 -->
    <div v-if="loading" class="sv-section__skeleton" aria-hidden="true">
      <span v-for="i in 2" :key="i" class="sv-section__bar" />
    </div>

    <div v-else-if="error !== null" class="sv-section__state" role="alert">
      <p class="sv-section__text">{{ error }}</p>
      <button class="sv-section__retry" type="button" @click="$emit('retry')">重试</button>
    </div>

    <div v-else-if="empty" class="sv-section__state">
      <p class="sv-section__text">{{ emptyText }}</p>
    </div>

    <slot v-else />
  </section>
</template>

<style scoped>
.sv-section {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
}

.sv-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sv-space-sm);
  padding: 0 var(--sv-space-xs);
}

.sv-section__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.sv-section__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
}

.sv-section__bar {
  height: 64px;
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  opacity: 0.7;
  animation: sv-pulse 1.4s ease-in-out infinite;
}

.sv-section__bar:nth-child(2) {
  animation-delay: 0.15s;
}

@keyframes sv-pulse {
  50% {
    opacity: 0.35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sv-section__bar {
    animation: none;
  }
}

.sv-section__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-xl) var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.sv-section__text {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  text-align: center;
}

.sv-section__retry {
  border: 0;
  padding: var(--sv-space-xs) var(--sv-space-md);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font: inherit;
  font-size: var(--sv-font-caption);
  cursor: pointer;
}

.sv-section__retry:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}
</style>
