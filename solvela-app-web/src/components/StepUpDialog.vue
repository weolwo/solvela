<script setup lang="ts">
import { ref, watch } from 'vue'

import { sendStepUpCode, verifyStepUp } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { useCodeSender } from '@/composables/useCodeSender'
import { finishStepUp, useStepUpState } from '@/composables/useStepUp'

/**
 * 敏感操作二次验证弹窗。
 *
 * <h3>什么时候出现</h3>
 * 在一台新设备上加收货地址、充话费时，服务端返回 STEP_UP_REQUIRED，
 * http 拦截器经 {@link useStepUpState} 唤起本弹窗。验证通过后，拦截器<b>自动重试原请求</b> ——
 * 用户看到的是「输个码，然后刚才那个操作就成了」，不用回头再点一次保存。
 *
 * <h3>打开就自动发码</h3>
 * 用户来到这里只有一件事要做，让他先点「获取验证码」只是多一步。
 * 倒计时、限频、失败提示仍由 useCodeSender 管，和其它验证码页一致。
 *
 * <h3>🔴 措辞</h3>
 * 不说「检测到异常」「你的设备被标记」—— 一台新手机是完全正常的事，
 * 那种话只会让人以为账号出了问题去找客服。只说为什么要验、码寄到了哪。
 */

const { open } = useStepUpState()

const code = ref('')
const codeError = ref<string | undefined>(undefined)
const maskedEmail = ref<string | null>(null)
const submitting = ref(false)

const sender = useCodeSender(async () => {
  maskedEmail.value = await sendStepUpCode()
})

watch(open, (isOpen) => {
  if (!isOpen) {
    return
  }
  code.value = ''
  codeError.value = undefined
  maskedEmail.value = null
  sender.reset()
  void sender.send()
})

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  const input = code.value.trim()
  if (input === '') {
    codeError.value = '请输入验证码'
    return
  }
  codeError.value = undefined
  submitting.value = true
  try {
    await verifyStepUp(input)
    finishStepUp(true)
  } catch (e) {
    codeError.value = e instanceof ApiError ? e.message : '验证失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

function cancel(): void {
  finishStepUp(false)
}
</script>

<template>
  <div v-if="open" class="mask" role="dialog" aria-modal="true" aria-labelledby="step-up-title">
    <form class="dialog" novalidate @submit.prevent="submit">
      <h2 id="step-up-title" class="dialog__title">验证是你本人</h2>

      <p class="dialog__intro">
        你正在一台新设备上修改收货信息或充值。为了账号安全，请输入发送到你绑定邮箱的验证码。
      </p>
      <p class="dialog__sent">
        {{ maskedEmail === null ? '验证码发送中…' : `验证码已发送到 ${maskedEmail}` }}
      </p>

      <Field
        v-model="code"
        icon="lock"
        type="tel"
        placeholder="邮箱验证码"
        autocomplete="one-time-code"
        :maxlength="6"
        :error="codeError ?? sender.error.value"
      >
        <template #suffix>
          <Button
            variant="text"
            type="button"
            :block="false"
            :disabled="!sender.canSend.value"
            @click="sender.send"
          >
            {{ sender.label.value }}
          </Button>
        </template>
      </Field>

      <Button type="submit" :loading="submitting">确认</Button>
      <Button variant="text" :disabled="submitting" @click="cancel">取消</Button>

      <!-- 收到这封信却不是自己在操作，本身就是一次盗号告警。告诉他下一步做什么 -->
      <p class="dialog__hint">如果这不是你本人的操作，请不要输入验证码，并尽快修改密码。</p>
    </form>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 110;
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
  line-height: 1.4;
  color: var(--sv-text-primary);
}

.dialog__intro,
.dialog__sent {
  margin: 0;
  font-size: var(--sv-font-body);
  line-height: 1.6;
  color: var(--sv-text-secondary);
}

.dialog__hint {
  margin: 0;
  font-size: var(--sv-font-caption);
  line-height: 1.5;
  color: var(--sv-text-placeholder);
  text-align: center;
}
</style>
