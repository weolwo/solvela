<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import { createCaptcha, type CaptchaChallenge, verifyCaptcha } from '@/api/captcha'
import { ApiError } from '@/api/errors'
import { finishCaptcha, useCaptchaState } from '@/composables/useCaptcha'
import { clampOffset, toImageX } from '@/utils/slider'

/**
 * 滑块验证码弹窗：拖动拼图块到缺口处。
 *
 * <h3>什么时候出现</h3>
 * 发验证码、密码登录时服务端返回 CAPTCHA_REQUIRED，http 拦截器经 {@link useCaptchaState} 唤起本弹窗。
 * 拖对了拿到通行票，拦截器带着它<b>自动重试原请求</b> —— 用户看到的是「拖一下，验证码就发出去了」。
 *
 * <h3>拖错了直接换一张</h3>
 * 服务端一张图只认一次，拖错后那张图已经作废，所以这里立刻换新图，而不是让他在同一张上再拖。
 *
 * <h3>滑块宽度 = 拼图宽度</h3>
 * 这样滑块往右移多少，拼图就往右移多少，用户对齐的是眼睛看到的缺口，不需要任何换算。
 */

const { open } = useCaptchaState()

const challenge = ref<CaptchaChallenge | null>(null)
const loading = ref(false)
const verifying = ref(false)
const message = ref<string | undefined>(undefined)
/** 滑块（和拼图）的显示偏移，px */
const offset = ref(0)
const stage = ref<HTMLElement | null>(null)
const stageWidth = ref(0)

let dragging = false
let dragStartX = 0

const scale = computed(() =>
  challenge.value === null || stageWidth.value === 0 ? 1 : stageWidth.value / challenge.value.width,
)
const handleWidth = computed(() => (challenge.value?.pieceSize ?? 0) * scale.value)

async function load(): Promise<void> {
  loading.value = true
  offset.value = 0
  try {
    challenge.value = await createCaptcha()
  } catch (e) {
    challenge.value = null
    message.value = e instanceof ApiError ? e.message : '加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
  await nextTick()
  stageWidth.value = stage.value?.clientWidth ?? 0
}

watch(open, (isOpen) => {
  if (isOpen) {
    message.value = undefined
    void load()
  }
})

function onPointerDown(e: PointerEvent): void {
  if (challenge.value === null || verifying.value || loading.value) {
    return
  }
  dragging = true
  dragStartX = e.clientX - offset.value
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
}

function onPointerMove(e: PointerEvent): void {
  if (!dragging) {
    return
  }
  offset.value = clampOffset(e.clientX - dragStartX, stageWidth.value, handleWidth.value)
}

async function onPointerUp(): Promise<void> {
  if (!dragging || challenge.value === null) {
    return
  }
  dragging = false
  verifying.value = true
  try {
    const token = await verifyCaptcha(
      challenge.value.captchaId,
      toImageX(offset.value, stageWidth.value, challenge.value.width),
    )
    finishCaptcha(token)
  } catch {
    message.value = '没有对上，换一张再试'
    await load()
  } finally {
    verifying.value = false
  }
}

function cancel(): void {
  finishCaptcha(null)
}
</script>

<template>
  <div v-if="open" class="mask" role="dialog" aria-modal="true" aria-labelledby="captcha-title">
    <div class="dialog">
      <h2 id="captcha-title" class="dialog__title">安全验证</h2>
      <p class="dialog__hint">{{ message ?? '向右拖动滑块，把拼图对准缺口' }}</p>

      <div
        ref="stage"
        class="stage"
        :style="{
          aspectRatio: challenge ? `${challenge.width} / ${challenge.height}` : '300 / 160',
        }"
      >
        <template v-if="challenge">
          <img class="stage__bg" :src="`data:image/png;base64,${challenge.background}`" alt="" />
          <img
            class="stage__piece"
            :src="`data:image/png;base64,${challenge.piece}`"
            alt=""
            :style="{
              left: `${offset}px`,
              top: `${challenge.pieceY * scale}px`,
              width: `${handleWidth}px`,
            }"
          />
        </template>
        <p v-else class="stage__loading">{{ loading ? '加载中…' : '' }}</p>
      </div>

      <div class="track">
        <div class="track__fill" :style="{ width: `${offset + handleWidth}px` }" />
        <!--
          touch-action: none 必须有：否则手机上一拖，浏览器把它当成页面滚动，
          pointermove 收不到，滑块纹丝不动。
        -->
        <button
          type="button"
          class="track__handle"
          aria-label="拖动滑块"
          :disabled="challenge === null || verifying"
          :style="{ left: `${offset}px`, width: `${Math.max(handleWidth, 40)}px` }"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
        >
          ⟶
        </button>
      </div>

      <div class="dialog__actions">
        <Button variant="text" :block="false" :disabled="loading || verifying" @click="load"
          >换一张</Button
        >
        <Button variant="text" :block="false" :disabled="verifying" @click="cancel">取消</Button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sv-space-page);
  background: rgb(0 0 0 / 45%);
}

.dialog {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  width: 100%;
  max-width: 340px;
  padding: var(--sv-space-lg);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  box-shadow: var(--sv-shadow-overlay);
}

.dialog__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  color: var(--sv-text-primary);
}

.dialog__hint {
  margin: 0;
  font-size: var(--sv-font-body);
  color: var(--sv-text-secondary);
}

.stage {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-page);
}

.stage__bg {
  display: block;
  width: 100%;
  height: 100%;
  user-select: none;
  pointer-events: none;
}

.stage__piece {
  position: absolute;
  user-select: none;
  pointer-events: none;
}

.stage__loading {
  margin: 0;
  padding: var(--sv-space-lg);
  text-align: center;
  color: var(--sv-text-placeholder);
}

.track {
  position: relative;
  height: 40px;
  border-radius: 20px;
  background: var(--sv-bg-page);
}

.track__fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 20px;
  background: var(--sv-color-primary-soft);
}

.track__handle {
  position: absolute;
  top: 0;
  height: 40px;
  border: none;
  border-radius: 20px;
  background: var(--sv-color-primary);
  color: #fff;
  font-size: 18px;
  touch-action: none;
  cursor: grab;
}

.track__handle:disabled {
  opacity: 0.6;
}

.dialog__actions {
  display: flex;
  justify-content: space-between;
}
</style>
