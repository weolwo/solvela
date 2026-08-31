<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/errors'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const phone = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)
/** 手机号已被注册时立起来：此时错误区要多给一个「去登录」的出口，光一行红字没用 */
const phoneTaken = ref(false)

/**
 * 两次密码不一致是**纯交互问题** —— 用户在同一个表单里打了两遍，前端自己就能判。
 * 所以后端的 MemberRegisterRequest 刻意没有 confirmPassword 字段：传上去再比一次
 * 发现不了任何前端发现不了的问题，只是多一个字段和一处可能不一致的措辞。
 */
const passwordMismatch = computed(
  () => confirmPassword.value !== '' && password.value !== confirmPassword.value,
)

const canSubmit = computed(
  () =>
    phone.value.trim() !== '' &&
    password.value !== '' &&
    confirmPassword.value !== '' &&
    !passwordMismatch.value &&
    !submitting.value,
)

/**
 * 刻意不在前端写手机号正则，也不在前端实现密码强度规则。
 *
 * 与 LoginView 同一个理由：手机号的格式校验在后端 MemberPhoneUtil.normalize 里，
 * 规范化和校验是同一件事的两面；密码规则在 MemberPasswordPolicy 里。
 * 前端各写一份，就是第二份规则，两份迟早对不上。
 *
 * 输入框下面那句提示是**文案**不是校验 —— 它可能与后端规则漂移，
 * 所以真正的权威是后端返回的 message（WEAK_PASSWORD 时 message 就是规则原文，
 * 这里原样展示）。
 */
async function submit(): Promise<void> {
  if (!canSubmit.value) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  errorTraceId.value = null
  phoneTaken.value = false

  try {
    await auth.register({ phone: phone.value.trim(), password: password.value, deviceType: 'H5' })
    // 注册即登录：后端把令牌一起返回了，直接进目标页，不再跳一次登录
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (error) {
    if (error instanceof ApiError) {
      errorMessage.value = error.message
      errorTraceId.value = error.traceId
      // 409：这个号已经有主了。给一个「去登录」的按钮，比让他自己找返回键强得多
      phoneTaken.value = error.code === 'CONFLICT'
    } else {
      errorMessage.value = '注册失败，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}

async function goLogin(): Promise<void> {
  await router.replace({ name: 'login', query: route.query })
}
</script>

<template>
  <div class="register">
    <h1 class="register__title">注册</h1>

    <div class="register__field">
      <var-input v-model="phone" variant="outlined" placeholder="手机号" type="tel" clearable />
    </div>

    <div class="register__field">
      <var-input v-model="password" variant="outlined" placeholder="密码" type="password" />
      <!-- 文案，不是校验。权威规则在后端 MemberPasswordPolicy -->
      <p class="register__hint">8-32 位，需同时包含字母和数字</p>
    </div>

    <div class="register__field">
      <var-input
        v-model="confirmPassword"
        variant="outlined"
        placeholder="再次输入密码"
        type="password"
      />
      <p v-if="passwordMismatch" class="register__error">两次输入的密码不一致</p>
    </div>

    <p v-if="errorMessage !== ''" class="register__error">
      {{ errorMessage }}
      <!-- traceId 一定要露出来：用户截图报障时，凭它一次定位到服务端日志 -->
      <span v-if="errorTraceId !== null" class="register__trace">（编号 {{ errorTraceId }}）</span>
    </p>

    <var-button v-if="phoneTaken" class="register__alt" type="primary" text block @click="goLogin">
      该手机号已注册，去登录
    </var-button>

    <var-button
      class="register__submit"
      type="primary"
      block
      :loading="submitting"
      :disabled="!canSubmit"
      @click="submit"
    >
      注册
    </var-button>

    <var-button class="register__alt" text block @click="goLogin">已有账号？去登录</var-button>
  </div>
</template>

<style scoped>
.register {
  padding: var(--sv-space-lg) var(--sv-space-md);
  padding-top: calc(var(--sv-safe-top) + 48px);
}

.register__title {
  margin: 0 0 var(--sv-space-lg);
  font-size: 24px;
  font-weight: 600;
}

.register__field {
  margin-bottom: var(--sv-space-md);
}

.register__hint {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-secondary);
  font-size: 12px;
}

.register__error {
  margin: var(--sv-space-xs) 0 var(--sv-space-md);
  color: var(--sv-color-danger);
  font-size: 13px;
}

.register__trace {
  color: var(--sv-text-secondary);
}

.register__submit {
  margin-top: var(--sv-space-sm);
}

.register__alt {
  margin-top: var(--sv-space-sm);
}
</style>
