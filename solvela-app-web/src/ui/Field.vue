<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import type { IconName } from './Icon.vue'

/**
 * 表单里的一个输入框：**胶囊填充式，左图标，密码可切明文**。
 *
 * <h3>为什么是独立胶囊而不是卡片里的一行</h3>
 * 卡片行（左标签右输入、行间分隔线）是「设置页」的语言 —— 一屏十几项、快速扫读。
 * 登录只有两三项，独立浮起的输入框把每一项都做成了明确的落点，
 * 左图标还额外给了一层「这一栏要填什么」的提示，不用等 placeholder 被输入顶掉。
 *
 * <h3>错误就在字段下面，不在按钮上面</h3>
 * 字段级的问题必须贴着字段说。用户看到「8-32 位，需同时包含字母和数字」
 * 却不知道是哪一栏，等于没说。
 * 整体性错误（账号密码不对、限流）才归表单，那种一句话就够。
 */

const model = defineModel<string>({ required: true })

const props = withDefaults(
  defineProps<{
    placeholder: string
    /** 左侧图标。鸿蒙这版靠它 + placeholder 说明这一栏要填什么，不用可见标签 */
    icon?: IconName
    /**
     * 左侧可见标签。<b>只在 iOS 皮肤下显示</b>，鸿蒙皮肤下它在 DOM 里但不可见。
     *
     * <h3>🔴 为什么是 CSS 控制显隐，不是读 store 判断</h3>
     * 读 store 的话，src/ui 下的基础组件就依赖了业务层的状态 ——
     * 而它们现在一个都不依赖。皮肤本来就是「一个 data-skin 属性 + 一层 CSS」，
     * 显隐也该由同一层管。
     *
     * <h3>传短词，不要把 placeholder 原样搬过来</h3>
     * 标签占的是左边固定的一列，「新手机号收到的验证码」这种长句会把输入框挤没。
     * 长提示留给 placeholder 或 hint —— 那两处本来就是横着排的。
     *
     * <p>不传的字段在 iOS 下保持「只有 placeholder」的样子。
     * 那也是 iOS 的常见形态（搜索框、验证码行），不是漏做。
     */
    label?: string | undefined
    type?: 'text' | 'tel' | 'password'
    /** 常态提示，一直显示。如注册页那句密码规则 */
    hint?: string | undefined
    /** 字段级错误。有值时替换 hint 显示 */
    error?: string | undefined
    autocomplete?: string | undefined
    maxlength?: number | undefined
  }>(),
  /*
   * 只有 type 需要默认值。
   *
   * 🔴 会变化的那几个 prop 声明成 `?: T | undefined` 是必须的，不是啰嗦：
   * tsconfig 开了 exactOptionalPropertyTypes，在那个开关下
   * 「没传这个 prop」和「传了 undefined」是两件事 —— 只写 `error?: string`
   * 的话，调用方 `:error="phoneError"`（类型是 string | undefined）传不进来。
   * 而校验函数把「没错误」表达成 undefined 正是最自然的写法。
   */
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

const message = computed(() => props.error ?? props.hint)

const labeled = computed(() => props.label !== undefined && props.label !== '')
</script>

<template>
  <div class="sv-field">
    <div
      class="sv-field__box"
      :class="{ 'sv-field__box--invalid': error !== undefined, 'sv-field__box--labeled': labeled }"
    >
      <Icon v-if="icon !== undefined" :name="icon" :size="20" class="sv-field__icon" />

      <!--
        iOS 那套表单行的左侧标签。用 <label for> 而不是 <span>：
        点标签能聚焦到输入框，这是浏览器免费给的，自己写 @click 反而更容易漏。
      -->
      <label v-if="labeled" class="sv-field__label" :for="inputId">{{ label }}</label>

      <input
        :id="inputId"
        v-model="model"
        class="sv-field__input"
        :type="inputType"
        :inputmode="inputMode"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :maxlength="maxlength"
        :aria-label="placeholder"
        :aria-invalid="error !== undefined"
        :aria-describedby="message === undefined ? undefined : `${inputId}-msg`"
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
        <Icon :name="revealed ? 'eye' : 'eye-off'" :size="20" />
      </button>

      <!--
        右侧附加控件，目前只有验证码那颗「获取」按钮用。
        做成插槽而不是再开一个 CodeField 组件：胶囊、聚焦描边、错误态、
        下方提示行这四样它们完全一致，复制一份的话四处样式要各改一遍，
        而漏改的表现是两个框在同一屏上长得不一样。
      -->
      <div v-if="$slots.suffix" class="sv-field__suffix">
        <slot name="suffix" />
      </div>
    </div>

    <p
      v-if="message !== undefined"
      :id="`${inputId}-msg`"
      class="sv-field__msg"
      :class="error === undefined ? 'sv-field__msg--hint' : 'sv-field__msg--error'"
    >
      {{ message }}
    </p>
  </div>
</template>

<style scoped>
.sv-field {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-xs);
}

.sv-field__box {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
  height: var(--sv-field-height);
  padding: 0 var(--sv-space-md);
  border: 1px solid transparent;
  border-radius: var(--sv-radius-field);
  background: var(--sv-bg-fill);
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
}

/*
 * ── iOS 皮肤：左侧可见标签 ──
 *
 * 默认整段不显示 —— 鸿蒙这版靠图标 + placeholder，多一个标签只会让每行更挤。
 */
.sv-field__label {
  display: none;
}

:root[data-skin='ios'] .sv-field__label {
  display: block;
  flex: none;
  color: var(--sv-text-primary);
  font-size: var(--sv-font-body);
  cursor: pointer;
}

/*
 * 🔴 有标签时把 placeholder 藏掉。
 * 两处写着同一个词（标签「手机号」+ 占位「手机号」）是最没用的一种重复，
 * 而且它会让人以为输入框里已经有内容了。
 *
 * 用 transparent 而不是清空 placeholder 属性：属性还在，读屏和
 * aria-label 照常工作，只是不画出来。
 */
:root[data-skin='ios'] .sv-field__box--labeled .sv-field__input::placeholder {
  color: transparent;
}

/* 有标签时输入靠右对齐 —— iOS 分组表单行就是这么排的 */
:root[data-skin='ios'] .sv-field__box--labeled .sv-field__input {
  text-align: right;
}

/* 聚焦时描一圈主色。边框始终占位（transparent），所以不会在聚焦瞬间抖动 */
.sv-field__box:focus-within {
  border-color: var(--sv-color-primary);
  background: var(--sv-bg-surface);
}

.sv-field__box--invalid {
  border-color: var(--sv-color-danger);
}

.sv-field__icon {
  color: var(--sv-text-placeholder);
}

.sv-field__box:focus-within .sv-field__icon {
  color: var(--sv-color-primary);
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

/* 右侧控件与输入框之间留一条界线，让它看起来是「另一个东西」而不是输入区的一部分 */
.sv-field__suffix {
  flex: none;
  display: flex;
  align-items: center;
  padding-left: var(--sv-space-sm);
  margin-right: calc(var(--sv-space-xs) * -1);
  border-left: 1px solid var(--sv-border-color);
}

.sv-field__reveal {
  flex: none;
  display: flex;
  border: 0;
  padding: var(--sv-space-xs);
  margin-right: calc(var(--sv-space-xs) * -1);
  background: transparent;
  color: var(--sv-text-placeholder);
  cursor: pointer;
}

.sv-field__reveal:active {
  color: var(--sv-text-secondary);
}

.sv-field__reveal:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 1px;
  border-radius: 50%;
}

.sv-field__msg {
  margin: 0;
  padding: 0 var(--sv-space-md);
  font-size: var(--sv-font-footnote);
  line-height: 1.4;
}

.sv-field__msg--hint {
  color: var(--sv-text-placeholder);
}

.sv-field__msg--error {
  color: var(--sv-color-danger);
}
</style>
