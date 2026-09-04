<script setup lang="ts">
/**
 * 按钮。
 *
 * <h3>为什么不用组件库</h3>
 * 这个文件一百来行，覆盖了我们全部的按钮需求。用组件库的话，我们要做的是
 * 把它的 Material 描边、涟漪、4px 圆角阶梯一层层 `::v-deep` 覆盖掉 ——
 * 最后维护的是「一套被改写过的 Material」，比自己写更难懂。
 *
 * 组件库该用在难写的地方（Picker 的手势惯性、Popup 的焦点陷阱与滚动锁定），
 * 不是这里。
 */

withDefaults(
  defineProps<{
    /** primary=主操作（实心胶囊）；text=次要出口（一行字，不占视觉重量） */
    variant?: 'primary' | 'text'
    loading?: boolean
    disabled?: boolean
    /**
     * 原生 type。默认 button —— 表单里要提交的那个必须显式写 submit，
     * 否则浏览器会把表单里第一个 button 当提交按钮，行为随 DOM 顺序漂
     */
    type?: 'button' | 'submit'
    block?: boolean
  }>(),
  {
    variant: 'primary',
    loading: false,
    disabled: false,
    type: 'button',
    block: true,
  },
)
</script>

<template>
  <button
    class="sv-btn"
    :class="[`sv-btn--${variant}`, { 'sv-btn--block': block }]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading"
  >
    <!-- 加载时保留文字，只在前面加一个转圈：换成纯图标会让按钮宽度跳变 -->
    <span v-if="loading" class="sv-btn__spinner" aria-hidden="true"></span>
    <slot />
  </button>
</template>

<style scoped>
.sv-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--sv-space-sm);
  height: var(--sv-control-height);
  padding: 0 var(--sv-space-lg);
  border: 0;
  border-radius: var(--sv-radius-pill);
  font-size: var(--sv-font-body);
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.sv-btn--block {
  display: flex;
  width: 100%;
}

.sv-btn--primary {
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
}

/* 移动端没有 hover，:active 才是真实反馈。加 @media 是为了不在触屏上误触发 hover 态 */
@media (hover: hover) {
  .sv-btn--primary:hover:not(:disabled) {
    background: var(--sv-color-primary-pressed);
  }
}

.sv-btn--primary:active:not(:disabled) {
  background: var(--sv-color-primary-pressed);
}

.sv-btn--text {
  height: auto;
  padding: var(--sv-space-sm);
  background: transparent;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.sv-btn--text:active:not(:disabled) {
  opacity: 0.6;
}

/*
 * 🔴 禁用态刻意做得【很轻】。
 *
 * 表单的主按钮不该在没填完时变灰不可点 —— 用户看不出差哪一项，只知道点不动。
 * 我们的登录/注册按钮始终可点，点了再告诉他缺什么。
 * 这里的 disabled 只用于「提交中」，那时变灰是对的：操作确实正在进行。
 */
.sv-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.sv-btn:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

.sv-btn__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentcolor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: sv-spin 0.7s linear infinite;
}

@keyframes sv-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sv-btn__spinner {
    animation-duration: 2s;
  }
}
</style>
