<script setup lang="ts">
import { ref } from 'vue'
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

const phoneError = ref<string | undefined>(undefined)
const passwordError = ref<string | undefined>(undefined)
const confirmError = ref<string | undefined>(undefined)

/** 文案，不是校验。权威规则在后端 MemberPasswordPolicy */
const PASSWORD_HINT = '8-32 位，需同时包含字母和数字'

/**
 * 刻意不在前端写手机号正则，也不在前端实现密码强度规则。
 *
 * 与 LoginView 同一个理由：手机号的格式校验在后端 MemberPhoneUtil.normalize 里，
 * 规范化和校验是同一件事的两面；密码规则在 MemberPasswordPolicy 里。
 * 前端各写一份，就是第二份规则，两份迟早对不上。
 *
 * 所以这里只判三件<b>前端自己就能确定</b>的事：三个框都填了、两次密码一致。
 * 「两次密码不一致」是纯交互问题 —— 用户在同一个表单里打了两遍，传上去再比一次
 * 发现不了任何前端发现不了的问题。后端 MemberRegisterRequest 因此刻意没有
 * confirmPassword 字段。
 */
function validate(): boolean {
  phoneError.value = phone.value.trim() === '' ? '请输入手机号' : undefined
  passwordError.value = password.value === '' ? '请设置密码' : undefined

  if (confirmPassword.value === '') {
    confirmError.value = '请再次输入密码'
  } else if (confirmPassword.value !== password.value) {
    confirmError.value = '两次输入的密码不一致'
  } else {
    confirmError.value = undefined
  }

  return (
    phoneError.value === undefined &&
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
  phoneTaken.value = false
  if (!validate()) {
    return
  }

  submitting.value = true
  try {
    await auth.register({ phone: phone.value.trim(), password: password.value, deviceType: 'H5' })
    // 注册即登录：后端把令牌一起返回了，直接进目标页，不再跳一次登录
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 'CONFLICT') {
        // 409：这个号已经有主了。挂到手机号字段上，并在下面给一个「去登录」的出口
        phoneTaken.value = true
        phoneError.value = error.message
      } else {
        // WEAK_PASSWORD 时后端的 message 就是规则原文，比前端那句提示更权威，原样展示
        errorMessage.value = error.message
      }
      errorTraceId.value = error.traceId
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
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">创建账号</h1>
      <p class="page__subtitle">手机号注册，注册完直接开抽</p>
    </header>

    <form class="page__form" novalidate @submit.prevent="submit">
      <SvField
        v-model="phone"
        icon="phone"
        type="tel"
        placeholder="手机号"
        autocomplete="username"
        :maxlength="11"
        :error="phoneError"
      />
      <SvField
        v-model="password"
        icon="lock"
        type="password"
        placeholder="设置密码"
        autocomplete="new-password"
        :hint="PASSWORD_HINT"
        :error="passwordError"
      />
      <SvField
        v-model="confirmPassword"
        icon="lock"
        type="password"
        placeholder="再次输入密码"
        autocomplete="new-password"
        :error="confirmError"
      />

      <p v-if="errorMessage !== ''" class="page__error" role="alert">
        {{ errorMessage }}
        <!-- traceId 一定要露出来：用户截图报障时，凭它一次定位到服务端日志 -->
        <span v-if="errorTraceId !== null" class="page__trace">（编号 {{ errorTraceId }}）</span>
      </p>

      <SvButton type="submit" :loading="submitting" class="page__submit">注册</SvButton>

      <!-- 号已被占用是唯一一种「用户下一步很明确」的失败，给个直达按钮比让他找返回键强 -->
      <SvButton v-if="phoneTaken" variant="text" @click="goLogin">该手机号已注册，去登录</SvButton>
    </form>

    <p class="page__alt">
      已有账号？<RouterLink class="page__link" :to="{ name: 'login', query: route.query }">
        去登录
      </RouterLink>
    </p>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: calc(var(--sv-safe-top) + var(--sv-space-xl)) var(--sv-space-page)
    calc(var(--sv-safe-bottom) + var(--sv-space-lg));
}

.page__head {
  margin-bottom: var(--sv-space-xl);
}

.page__title {
  margin: 0;
  font-size: var(--sv-font-title);
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.page__subtitle {
  margin: var(--sv-space-sm) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
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

.page__submit {
  margin-top: var(--sv-space-sm);
}

.page__alt {
  margin: var(--sv-space-xl) 0 0;
  padding-top: var(--sv-space-lg);
  text-align: center;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.page__link {
  color: var(--sv-color-primary);
  font-weight: 500;
  text-decoration: none;
}

.page__link:active {
  opacity: 0.6;
}
</style>
