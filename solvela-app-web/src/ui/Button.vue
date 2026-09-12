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
    /**
     * primary 主操作 / text 次要文字按钮 / danger 破坏性操作。
     *
     * 🔴 danger 不是「红色的 text」：破坏性操作要看得出<b>可点</b>，
     * 所以它有描边和底色。做成一行灰字的话，用户既看不出它危险，
     * 也看不出它是个按钮 —— 而「下线」这种动作恰恰要两样都看得出来。
     */
    variant?: 'primary' | 'text' | 'danger'
    loading?: boolean
    disabled?: boolean
    /**
     * 原生 type。默认 button —— 表单里要提交的那个必须显式写 submit，
     * 否则浏览器会把表单里第一个 button 当提交按钮，行为随 DOM 顺序漂
     */
    type?: 'button' | 'submit'
    /**
     * 占满整行。<b>默认 true</b> —— 本项目的按钮大多是表单主按钮。
     *
     * 🔴 放进一行 flex 布局里时<b>必须显式关掉</b>：
     * {@code width: 100%} 会让它把同排的文字列挤成 0 宽，
     * 而那一列会变成「每行一个字」竖着排下来。
     * 2026-09-10 登录设备那一页就是这么被挤坏的 —— 页面不报错，只是很丑。
     */
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
  border-radius: var(--sv-radius-control);
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
 * 破坏性操作：描边 + 浅底，不是实心红。
 * 实心红在列表里会变成最抢眼的东西 —— 而这一页真正要抢眼的是
 * 「哪台是我的」，不是每一行的下线按钮。
 */
/*
 * danger 天生是【行内】的操作按钮（列表某一行右侧那个），
 * 所以它自己把 block 撑开的宽度收回来。调用方仍可显式传 block 覆盖。
 */
.sv-btn--danger.sv-btn--block {
  display: inline-flex;
  width: auto;
}

.sv-btn--danger {
  flex: none;
  height: 32px;
  padding: 0 var(--sv-space-md);
  border: 1px solid var(--sv-color-danger);
  background: transparent;
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

@media (hover: hover) {
  .sv-btn--danger:hover:not(:disabled) {
    background: var(--sv-color-danger);
    color: var(--sv-text-on-primary);
  }
}

.sv-btn--danger:active:not(:disabled) {
  background: var(--sv-color-danger);
  color: var(--sv-text-on-primary);
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
