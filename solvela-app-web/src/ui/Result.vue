<script setup lang="ts">
/**
 * 操作结果反馈：遮罩上一张居中卡片，比如「登录成功」。
 *
 * <h3>这个可以自己写，Dialog 不行</h3>
 * 项目的原则是「要碰 teleport / 滚动锁定 / 焦点陷阱 / 手势惯性 的别自己写」。
 * 本组件确实用了 teleport，但**没有任何可交互元素** —— 它只是显示一下就消失，
 * 用户不需要（也不能）用键盘在里面移动。焦点陷阱是为了「不让 Tab 跑到遮罩后面」，
 * 而这里根本没有可 Tab 的目标。所以那条原则真正拦的是 Dialog / Picker，不是它。
 *
 * <h3>用 role="status" 而不是 alert</h3>
 * 成功不是警报。`alert` 会打断读屏当前朗读，`status` 排队播报 —— 对一句
 * 「登录成功」，打断是过度的。
 */

defineProps<{
  open: boolean
  text: string
}>()
</script>

<template>
  <Teleport to="body">
    <Transition name="sv-result">
      <div v-if="open" class="sv-result" role="status" aria-live="polite">
        <div class="sv-result__card">
          <div class="sv-result__badge">
            <Icon name="check" :size="34" />
          </div>
          <p class="sv-result__text">{{ text }}</p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sv-result {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sv-space-page);
  /* 遮罩很淡：这是一次成功反馈，不是需要用户决策的弹窗，不该把页面压黑 */
  background: rgb(24 36 49 / 12%);
  backdrop-filter: blur(2px);
}

.sv-result__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-md);
  width: 100%;
  max-width: 240px;
  padding: var(--sv-space-lg);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  box-shadow: 0 12px 32px -12px rgb(24 36 49 / 30%);
}

.sv-result__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
  /* 外圈光晕：用 box-shadow 而不是多套一层 div */
  box-shadow: 0 0 0 8px var(--sv-color-primary-soft);
}

.sv-result__text {
  margin: 0;
  font-size: var(--sv-font-heading);
  font-weight: 600;
}

.sv-result-enter-active,
.sv-result-leave-active {
  transition: opacity 0.18s ease;
}

.sv-result-enter-active .sv-result__card {
  transition: transform 0.22s cubic-bezier(0.2, 1.2, 0.4, 1);
}

.sv-result-enter-from,
.sv-result-leave-to {
  opacity: 0;
}

.sv-result-enter-from .sv-result__card {
  transform: scale(0.9);
}

@media (prefers-reduced-motion: reduce) {
  .sv-result-enter-active .sv-result__card {
    transition: none;
  }
}
</style>
