<script setup lang="ts">
/**
 * 白色圆角卡片。承载表单行、余额、奖品列表 —— 全站的内容都住在这种卡里。
 *
 * <p>分隔线由卡片画，不由行画：让每一行自己画下边框的话，最后一行那条必须
 * 再写一条规则去掉，而「最后一行」在 v-for 里是会变的。
 * 由卡片用 `:not(:first-child)` 画，行数怎么变都不会多出一条线。
 */

withDefaults(
  defineProps<{
    /** 关掉行间分隔线。展示型卡片（余额、单段文案）用不上 */
    divided?: boolean
  }>(),
  { divided: true },
)
</script>

<template>
  <div class="sv-card" :class="{ 'sv-card--divided': divided }">
    <slot />
  </div>
</template>

<style scoped>
.sv-card {
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  overflow: hidden;
}

/*
 * :deep 是必要的：分隔线要画在插槽内容上，而插槽内容属于父组件的作用域。
 * 只用相邻兄弟选择器，不假设子元素是什么组件 —— 表单行、列表项、自定义块都能放进来。
 */
.sv-card--divided :deep(> * + *) {
  box-shadow: inset 0 0.5px 0 var(--sv-border-color);
}
</style>
