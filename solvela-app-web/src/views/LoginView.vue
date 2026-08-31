<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/errors'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 带着 redirect 一起跳，注册完能回到用户本来要去的页面 */
async function goRegister(): Promise<void> {
  await router.replace({ name: 'register', query: route.query })
}

const phone = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)

const canSubmit = computed(
  () => phone.value.trim() !== '' && password.value !== '' && !submitting.value,
)

/**
 * 刻意不在前端写手机号正则。
 *
 * 后端 LoginRequest 的注释说得很清楚：格式校验在 MemberPhoneUtil.normalize 里，
 * 规范化和校验是同一件事的两面。前端再写一条正则就是第二份手机号规则，两份迟早对不上。
 * 这里只做「非空」这种纯交互层面的拦截。
 */
async function submit(): Promise<void> {
  if (!canSubmit.value) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  errorTraceId.value = null

  try {
    await auth.login({ phone: phone.value.trim(), password: password.value, deviceType: 'H5' })
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (error) {
    if (error instanceof ApiError) {
      // BAD_CREDENTIALS / ACCOUNT_DISABLED / OPERATION_LIMITED 都在这里展示后端给的人话。
      // 注意它们里有 401，但拦截器不会把它们当成「掉登录态」，所以能走到这一行。
      errorMessage.value = error.message
      errorTraceId.value = error.traceId
    } else {
      errorMessage.value = '登录失败，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <h1 class="login__title">登录</h1>

    <div class="login__field">
      <var-input v-model="phone" variant="outlined" placeholder="手机号" type="tel" clearable />
    </div>

    <div class="login__field">
      <var-input v-model="password" variant="outlined" placeholder="密码" type="password" />
    </div>

    <p v-if="errorMessage !== ''" class="login__error">
      {{ errorMessage }}
      <!-- traceId 一定要露出来：用户截图报障时，凭它一次定位到服务端日志 -->
      <span v-if="errorTraceId !== null" class="login__trace">（编号 {{ errorTraceId }}）</span>
    </p>

    <var-button
      class="login__submit"
      type="primary"
      block
      :loading="submitting"
      :disabled="!canSubmit"
      @click="submit"
    >
      登录
    </var-button>

    <var-button class="login__alt" text block @click="goRegister">还没有账号？去注册</var-button>
  </div>
</template>

<style scoped>
.login {
  padding: var(--sv-space-lg) var(--sv-space-md);
  padding-top: calc(var(--sv-safe-top) + 48px);
}

.login__title {
  margin: 0 0 var(--sv-space-lg);
  font-size: 24px;
  font-weight: 600;
}

.login__field {
  margin-bottom: var(--sv-space-md);
}

.login__error {
  margin: 0 0 var(--sv-space-md);
  color: var(--sv-color-danger);
  font-size: 13px;
}

.login__trace {
  color: var(--sv-text-secondary);
}

.login__submit {
  margin-top: var(--sv-space-sm);
}

.login__alt {
  margin-top: var(--sv-space-sm);
}
</style>
