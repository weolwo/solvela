<script setup lang="ts">
import { computed, ref, useId } from 'vue'

/**
 * 表单里的一行输入。放进 {@link SvCard} 里，卡片负责分隔线和圆角。
 *
 * <h3>错误就在字段下面，不在按钮上面</h3>
 * 上一版所有错误都堆在主按钮上方一段红字里 —— 用户看到「8-32 位，需同时包含
 * 字母和数字」得自己猜是哪一栏。字段级的问题必须贴着字段说。
 * 整体性错误（账号密码不对、限流）才归表单，那种一句话就够，不存在指错栏的问题。
 */

const model = defineModel<string>({ required: true })

const props = withDefaults(
  defineProps<{
    placeholder: string
    /**
     * 左侧可见标签。鸿蒙这版不传（靠 placeholder），
     * 换成 iOS 观感时要传 —— iOS 的表单行是「左标签右输入」
     */
    label?: string
    type?: 'text' | 'tel' | 'password'
    /** 常态提示，一直显示。如注册页那句密码规则 */
    hint?: string
    /** 字段级错误。有值时替换 hint 显示 */
    error?: string
    autocomplete?: string
    maxlength?: number
  }>(),
  // 只有 type 需要默认值。其余可选 prop 不写默认 ——
  // tsconfig 开了 exactOptionalPropertyTypes，显式写 undefined 反而是类型错误：
  // 「没传这个 prop」和「传了 undefined」在那个开关下是两件事
  { type: 'text' },
)

const inputId = useId()
const revealed = ref(false)

const isPassword = computed(() => props.type === 'password')

/** 明文切换后 type 要真的变成 text，否则 iOS 上仍然是圆点 */
const inputType = computed(() => (isPassword.value && revealed.value ? 'text' : props.type))

/**
 * 数字键盘只给手机号。密码用默认键盘 —— 密码要求含字母，
 * 弹数字键盘等于逼用户自己切换，注册失败率会明显上去
 */
const inputMode = computed(() => (props.type === 'tel' ? 'numeric' : undefined))

const describedBy = computed(() =>
  (props.error ?? props.hint) !== undefined ? `${inputId}-msg` : undefined,
)
</script>

<template>
  <div class="sv-field" :class="{ 'sv-field--invalid': error !== undefined }">
    <div class="sv-field__row">
      <label v-if="label !== undefined" class="sv-field__label" :for="inputId">{{ label }}</label>

      <input
        :id="inputId"
        v-model="model"
        class="sv-field__input"
        :type="inputType"
        :inputmode="inputMode"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :maxlength="maxlength"
        :aria-label="label === undefined ? placeholder : undefined"
        :aria-invalid="error !== undefined"
        :aria-describedby="describedBy"
      />

      <!--
        明文切换。注册页要求「8-32 位且含字母和数字」，看不见输入内容会显著
        拉高失败率 —— 用户打错一个字符要全删重来。
        用 button 而不是 span：它是可聚焦、可用键盘触发的控件。
        type="button" 必须写，否则在 <form> 里点它会触发提交。
      -->
      <button
        v-if="isPassword"
        class="sv-field__reveal"
        type="button"
        :aria-label="revealed ? '隐藏密码' : '显示密码'"
        :aria-pressed="revealed"
        @click="revealed = !revealed"
      >
        {{ revealed ? '隐藏' : '显示' }}
      </button>
    </div>

    <p v-if="error !== undefined" :id="`${inputId}-msg`" class="sv-field__error">{{ error }}</p>
    <p v-else-if="hint !== undefined" :id="`${inputId}-msg`" class="sv-field__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.sv-field {
  padding: 0 var(--sv-space-md);
}

.sv-field__row {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  min-height: var(--sv-row-height);
}

.sv-field__label {
  flex: none;
  width: 60px;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.sv-field__input {
  flex: 1;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--sv-text-primary);
  font: inherit;
  font-size: var(--sv-font-body);
  /* iOS 会给 input 加内阴影和圆角，不清掉就永远像个 Web 表单 */
  appearance: none;
  border-radius: 0;
}

.sv-field__input::placeholder {
  color: var(--sv-text-placeholder);
}

.sv-field__input:focus {
  outline: none;
}

/* 整行给焦点提示，而不是给 input 自己描边 —— 描边会把行高撑开 */
.sv-field:focus-within .sv-field__label {
  color: var(--sv-text-primary);
}

.sv-field__reveal {
  flex: none;
  border: 0;
  padding: var(--sv-space-xs) 0 var(--sv-space-xs) var(--sv-space-sm);
  background: transparent;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
  cursor: pointer;
}

.sv-field__reveal:active {
  color: var(--sv-text-secondary);
}

.sv-field__reveal:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
  border-radius: var(--sv-radius-sm);
}

.sv-field__hint,
.sv-field__error {
  margin: 0;
  padding-bottom: var(--sv-space-sm);
  font-size: var(--sv-font-footnote);
  line-height: 1.4;
}

.sv-field__hint {
  color: var(--sv-text-placeholder);
}

.sv-field__error {
  color: var(--sv-color-danger);
}
</style>
