<script setup lang="ts">
/**
 * 忘记密码：用邮箱验证码重置。
 *
 * <h3>🔴 这是匿名页 —— 用户正是因为进不去才走这条路</h3>
 * 所以路由上必须标 `anonymous`。要求登录才能重置密码是个死循环，
 * 而这类错误在实现时一点都不明显：开发自己总是登录着的。
 *
 * <h3>手机号与邮箱两条都支持，默认手机号</h3>
 * 手机号是这个系统<b>注册的默认身份</b> —— 绝大多数用户注册完没绑邮箱，
 * 默认成邮箱的话，他打开这一页第一眼看到的就是一个自己填不了的框。
 *
 * <p>⚠️ 两条都没有的账号（邮箱注册且没绑手机、又忘了密码）仍然自助找不回来。
 * 这种情况要如实说出来，而不是让他填半天再撞一句「验证码错误」。
 *
 * <h3>成功之后要把「已在几台设备上退出登录」显示出来</h3>
 * 点「忘记密码」的最常见原因之一就是「我怀疑号被人动过」。
 * 那个数字正是他要的答案 —— 只说一句「修改成功」，这条信息就白丢了。
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { resetPassword, sendEmailCode, sendSmsCode, type PasswordResetType } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { useCodeSender } from '@/composables/useCodeSender'

const router = useRouter()

/** 文案，不是校验。权威规则在后端 MemberPasswordPolicy */
const PASSWORD_HINT = '8-32 位，需同时包含字母和数字'

/**
 * 手机号排在前面 —— 它是注册的默认身份，绝大多数用户走这条。
 * 把邮箱放前面的话，多数人第一眼看到的是一个自己填不了的框。
 */
const RESET_OPTIONS = [
  { value: 'SMS_CODE', label: '手机号' },
  { value: 'EMAIL_CODE', label: '邮箱' },
] as const

const resetType = ref<PasswordResetType>('SMS_CODE')
const byPhone = computed(() => resetType.value === 'SMS_CODE')

const identity = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

const identityError = ref<string | undefined>(undefined)
const codeError = ref<string | undefined>(undefined)
const passwordError = ref<string | undefined>(undefined)
const confirmError = ref<string | undefined>(undefined)
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)
const submitting = ref(false)
/** 成功之后被吊销的会话数。null 表示还没成功 */
const revokedSessions = ref<number | null>(null)

const codeSender = useCodeSender(
  () =>
    byPhone.value
      ? sendSmsCode('RESET_PASSWORD', identity.value.trim())
      : sendEmailCode('RESET_PASSWORD', identity.value.trim()),
  {
    precheck: () => {
      if (identity.value.trim() === '') {
        identityError.value = byPhone.value ? '请先输入手机号' : '请先输入邮箱'
        return false
      }
      return true
    },
  },
)

/*
 * 换了通道或换了身份，之前那个码就是发给别处的了 —— 状态必须跟着清。
 * 不清的话：用户给邮箱发了码，切到手机号，那个码还躺在框里，
 * 提交时报「验证码错误」，而他明明刚收到过一条。
 */
watch([resetType, identity], () => {
  code.value = ''
  codeError.value = undefined
  identityError.value = undefined
  errorMessage.value = ''
  codeSender.reset()
})

function validate(): boolean {
  identityError.value =
    identity.value.trim() === '' ? (byPhone.value ? '请输入手机号' : '请输入邮箱') : undefined
  codeError.value = code.value.trim() === '' ? '请输入验证码' : undefined
  passwordError.value = newPassword.value === '' ? '请设置新密码' : undefined

  if (confirmPassword.value === '') {
    confirmError.value = '请再次输入新密码'
  } else if (confirmPassword.value !== newPassword.value) {
    confirmError.value = '两次输入的密码不一致'
  } else {
    confirmError.value = undefined
  }

  return (
    identityError.value === undefined &&
    codeError.value === undefined &&
    passwordError.value === undefined &&
    confirmError.value === undefined
  )
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  errorMessage.value = ''
  errorTraceId.value = null
  if (!validate()) {
    return
  }
  submitting.value = true
  try {
    const result = await resetPassword({
      resetType: resetType.value,
      identity: identity.value.trim(),
      code: code.value.trim(),
      newPassword: newPassword.value,
    })
    revokedSessions.value = result.revokedSessions
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 'BAD_CREDENTIALS') {
        /*
         * 这条路上的 401 只有一个来源：验证码不对/失效/错太多次。
         * 🔴 「这个邮箱没注册过」【不会】走到这里 —— 服务端对未注册的邮箱
         * 也照常存码、照常返回同样的错误，否则这个页面就成了账号枚举接口。
         */
        codeError.value = error.message
      } else {
        // WEAK_PASSWORD 时后端的 message 就是规则原文，比前端那句提示更权威
        errorMessage.value = error.message
      }
      errorTraceId.value = error.traceId
    } else {
      errorMessage.value = '重置失败，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}

async function goLogin(): Promise<void> {
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="page">
    <NavBar title="重置密码" />

    <div class="page__body">
      <!-- 成功之后整页换成结果，而不是在表单上飘一个 toast：
           那个「已在 N 台设备退出登录」是用户要停下来读的信息 -->
      <div v-if="revokedSessions !== null" class="done">
        <span class="done__badge"><Icon name="check" :size="34" /></span>
        <p class="done__title">密码已重置</p>
        <p class="done__text">
          为了账号安全，已在
          <b>{{ revokedSessions }}</b>
          台设备上退出登录。请用新密码重新登录。
        </p>
        <Button @click="goLogin">去登录</Button>
      </div>

      <template v-else>
        <p class="page__intro">
          {{
            byPhone
              ? '输入你注册时用的手机号，我们会发一条验证码短信。'
              : '输入你绑定的邮箱，我们会发一封验证码邮件。'
          }}
          <br />
          <span class="page__note">手机号和邮箱都没绑过的账号无法自助重置，请联系客服。</span>
        </p>

        <form class="page__form" novalidate @submit.prevent="submit">
          <Segmented v-model="resetType" :options="RESET_OPTIONS" />

          <Field
            v-model="identity"
            :icon="byPhone ? 'phone' : 'user'"
            :type="byPhone ? 'tel' : 'text'"
            :label="byPhone ? '手机号' : '邮箱'"
            :placeholder="byPhone ? '注册手机号' : '账号邮箱'"
            autocomplete="username"
            :maxlength="byPhone ? 11 : undefined"
            :error="identityError"
          />
          <Field
            v-model="code"
            icon="lock"
            type="tel"
            :placeholder="byPhone ? '短信验证码' : '邮箱验证码'"
            autocomplete="one-time-code"
            :maxlength="6"
            :error="codeError ?? codeSender.error.value"
          >
            <template #suffix>
              <Button
                variant="text"
                type="button"
                :block="false"
                :disabled="!codeSender.canSend.value"
                @click="codeSender.send"
              >
                {{ codeSender.label.value }}
              </Button>
            </template>
          </Field>
          <Field
            v-model="newPassword"
            icon="lock"
            type="password"
            label="新密码"
            placeholder="新密码"
            autocomplete="new-password"
            :hint="PASSWORD_HINT"
            :error="passwordError"
          />
          <Field
            v-model="confirmPassword"
            icon="lock"
            type="password"
            label="确认密码"
            placeholder="再次输入新密码"
            autocomplete="new-password"
            :error="confirmError"
          />

          <p v-if="errorMessage !== ''" class="page__error" role="alert">
            {{ errorMessage }}
            <span v-if="errorTraceId !== null" class="page__trace"
              >（编号 {{ errorTraceId }}）</span
            >
          </p>

          <Button type="submit" :loading="submitting">重置密码</Button>
        </form>
      </template>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.page__body {
  flex: 1;
  padding: var(--sv-space-md) var(--sv-space-page) calc(var(--sv-safe-bottom) + var(--sv-space-lg));
}

.page__intro {
  margin: 0 0 var(--sv-space-lg);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.6;
}

.page__note {
  color: var(--sv-text-placeholder);
}

.page__form {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
}

.page__error {
  margin: 0;
  padding: 0 var(--sv-space-md);
  color: var(--sv-color-danger);
  font-size: var(--sv-font-footnote);
  line-height: 1.5;
}

.page__trace {
  color: var(--sv-text-placeholder);
}

.done {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-xl) var(--sv-space-md);
}

.done__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.done__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  font-weight: 600;
}

.done__text {
  margin: 0;
  text-align: center;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.6;
}
</style>
