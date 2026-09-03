<script setup lang="ts">
import { useId } from 'vue'

/**
 * 复选框。
 *
 * <p>包了一层自定义外观，但**里面是真的 `<input type="checkbox">`** ——
 * 用 div 模拟的话，键盘 Tab 到不了、空格键不响应、读屏不报「复选框 已勾选」，
 * 而这三件事浏览器本来免费给你。原生 input 视觉上藏起来（不是 `display:none`，
 * 那会让它彻底退出无障碍树），外观用相邻的 span 画。
 */

const model = defineModel<boolean>({ required: true })

defineProps<{ label: string }>()

const id = useId()
</script>

<template>
  <div class="sv-checkbox">
    <input :id="id" v-model="model" class="sv-checkbox__native" type="checkbox" />
    <span class="sv-checkbox__box" aria-hidden="true">
      <SvIcon name="check" :size="14" />
    </span>
    <label class="sv-checkbox__label" :for="id">{{ label }}</label>
  </div>
</template>

<style scoped>
.sv-checkbox {
  display: inline-flex;
  align-items: center;
  gap: var(--sv-space-sm);
  position: relative;
}

/*
 * 视觉上移除但保留在无障碍树里。
 * 用 opacity:0 + 绝对定位盖在方框上，这样点击原生 input 的命中区域
 * 与画出来的方框重合，不需要额外的 label 包裹技巧。
 */
.sv-checkbox__native {
  position: absolute;
  left: 0;
  width: 20px;
  height: 20px;
  margin: 0;
  opacity: 0;
  cursor: pointer;
}

.sv-checkbox__box {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 1.5px solid var(--sv-border-strong);
  border-radius: var(--sv-radius-sm);
  background: var(--sv-bg-surface);
  color: transparent;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease;
}

.sv-checkbox__native:checked + .sv-checkbox__box {
  border-color: var(--sv-color-primary);
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
}

.sv-checkbox__native:focus-visible + .sv-checkbox__box {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

.sv-checkbox__label {
  color: var(--sv-text-primary);
  font-size: var(--sv-font-caption);
  cursor: pointer;
  user-select: none;
}
</style>
