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
const submitting = ref(false)
/** 整体性错误（账号密码不对、账号被禁、限流）。字段级的问题走 fieldError */
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)
const phoneError = ref<string | undefined>(undefined)
const passwordError = ref<string | undefined>(undefined)

/**
 * 刻意不在前端写手机号正则。
 *
 * 后端 LoginRequest 的注释说得很清楚：格式校验在 MemberPhoneUtil.normalize 里，
 * 规范化和校验是同一件事的两面。前端再写一条正则就是第二份手机号规则，两份迟早对不上。
 * 这里只做「非空」这种纯交互层面的拦截。
 *
 * 🔴 而且是在【点击之后】拦，不是把按钮置灰。
 * 上一版用 :disabled="!canSubmit"，没填完时按钮是灰的 —— 用户看不出差哪一项，
 * 只知道点不动。始终可点、点了再指出缺什么，是移动端更常见也更好用的做法。
 */
function validate(): boolean {
  phoneError.value = phone.value.trim() === '' ? '请输入手机号' : undefined
  passwordError.value = password.value === '' ? '请输入密码' : undefined
  return phoneError.value === undefined && passwordError.value === undefined
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
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">欢迎回来</h1>
      <p class="page__subtitle">登录后继续参与本期抽奖</p>
    </header>

    <!--
      用 <form> 包起来是为了拿到两件浏览器免费给的东西：
      密码框回车直接提交，以及移动端键盘把回车键变成「前往」。
      自己监听 keyup.enter 做不到后者。
    -->
    <form class="page__form" novalidate @submit.prevent="submit">
      <SvCard>
        <SvField
          v-model="phone"
          type="tel"
          placeholder="手机号"
          autocomplete="username"
          :maxlength="11"
          :error="phoneError"
        />
        <SvField
          v-model="password"
          type="password"
          placeholder="密码"
          autocomplete="current-password"
          :error="passwordError"
        />
      </SvCard>

      <p v-if="errorMessage !== ''" class="page__error" role="alert">
        {{ errorMessage }}
        <!-- traceId 一定要露出来：用户截图报障时，凭它一次定位到服务端日志 -->
        <span v-if="errorTraceId !== null" class="page__trace">（编号 {{ errorTraceId }}）</span>
      </p>

      <SvButton type="submit" :loading="submitting" class="page__submit">登录</SvButton>
    </form>

    <p class="page__alt">
      还没有账号？<RouterLink class="page__link" :to="{ name: 'register', query: route.query }">
        注册
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
  margin-bottom: var(--sv-space-lg);
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
  padding: 0 var(--sv-space-sm);
  color: var(--sv-color-danger);
  font-size: var(--sv-font-footnote);
  line-height: 1.5;
}

/* 编号退到最后，但一直在 */
.page__trace {
  color: var(--sv-text-placeholder);
}

.page__submit {
  margin-top: var(--sv-space-sm);
}

/*
 * 次要出口就是一行小字。
 * 上一版是 <var-button text block>，占满整行跟主按钮抢视觉重量。
 * margin-top: auto 把它推到页面底部，中间的空白留给内容。
 */
.page__alt {
  margin: var(--sv-space-xl) 0 0;
  text-align: center;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.page__link {
  color: var(--sv-color-primary);
  text-decoration: none;
}

.page__link:active {
  opacity: 0.6;
}
</style>
