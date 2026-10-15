<script setup lang="ts">
import type { LoginAlternative } from '@/api/errors'

/**
 * 登录被挡下时的弹窗。两种情况共用：密码阶梯冷却、没有可用的核身通道。
 *
 * <h3>为什么这两种要弹窗，而限流只是一行红字</h3>
 * 判据是**用户此刻还能不能做点什么**：
 * <ul>
 *   <li>密码冷却 —— 还有两条路（验证码登录、找回密码）。那两颗按钮必须足够显眼，
 *       否则用户只会盯着倒计时等，而他本来根本不用等；</li>
 *   <li>没有核身通道 —— 自己什么也做不了，必须让他知道要找客服，
 *       一行小红字他会当成「再试一次」；</li>
 *   <li>被限流 —— 唯一的出路就是等。弹窗除了多一次点击什么也没给，
 *       所以那一档只在按钮上走倒计时（方案 6.4：这一档的对象主要是脚本，
 *       给真人的出路是等）。</li>
 * </ul>
 *
 * <h3>🔴 出路由服务端的 `alternatives` 决定，不在这里猜</h3>
 * 手机号登录时它是**空数组** —— 验证码登录只有邮箱那条，而手机号找回密码同样要短信，
 * 短信厂商还没接。自己按登录方式推一遍的话，用户会点到一个发不出码的页面，
 * 那比没有按钮更糟。
 */

const props = defineProps<{
  title: string
  message: string
  /** 倒计时文案（`00:59`）。没有倒计时就不传 */
  countdown?: string | null
  /** 服务端给的出路。空数组或不传就只有「知道了」 */
  alternatives?: LoginAlternative[]
}>()

const open = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  codeLogin: []
  passwordReset: []
}>()

function has(alternative: LoginAlternative): boolean {
  return props.alternatives?.includes(alternative) === true
}

function close(): void {
  open.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  }
}
</script>

<template>
  <Teleport to="body">
    <!--
      role="alertdialog"：这是需要用户响应才能继续的东西，读屏软件会据此改变播报方式。
      点遮罩本身可以关（.self）—— 和 Sheet 一样，不然在弹窗里选一段文字、
      鼠标松开时飘到遮罩上，弹窗就没了。
    -->
    <div
      v-if="open"
      class="mask"
      role="alertdialog"
      aria-modal="true"
      :aria-label="title"
      tabindex="-1"
      @click.self="close"
      @keydown="onKeydown"
    >
      <div class="dialog">
        <h2 class="dialog__title">{{ title }}</h2>
        <p class="dialog__message">{{ message }}</p>

        <!--
          倒计时用等宽数字，否则每秒都在轻微抖动 —— 一个一直在抖的数字会一直吸着眼睛，
          而这里要的恰恰是让人把注意力移到下面那两颗按钮上。
        -->
        <p v-if="countdown !== null && countdown !== undefined" class="dialog__countdown">
          {{ countdown }}
        </p>

        <div class="dialog__actions">
          <Button v-if="has('CODE_LOGIN')" @click="emit('codeLogin')">用验证码登录</Button>
          <Button v-if="has('PASSWORD_RESET')" variant="text" @click="emit('passwordReset')">
            找回密码
          </Button>
          <Button variant="text" @click="close">知道了</Button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sv-space-page);
  background: rgb(0 0 0 / 45%);
}

.dialog {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
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
  line-height: 1.4;
  color: var(--sv-text-primary);
}

.dialog__message {
  margin: 0;
  font-size: var(--sv-font-body);
  line-height: 1.7;
  color: var(--sv-text-secondary);
}

.dialog__countdown {
  margin: var(--sv-space-xs) 0;
  text-align: center;
  font-size: var(--sv-font-title);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--sv-text-primary);
}

.dialog__actions {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-sm);
}
</style>
