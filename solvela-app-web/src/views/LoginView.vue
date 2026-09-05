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
/**
 * 默认勾上。令牌有效期 30 天就是为了让人别每次都登，
 * 默认不勾等于把那个配置作废了。共用设备的人会自己取消。
 */
const remember = ref(true)
const submitting = ref(false)
/** 成功后先显示一下再跳，用户需要一个「确实成了」的确认 */
const succeeded = ref(false)

/** 整体性错误（账号密码不对、账号被禁、限流）。字段级的问题走 xxxError */
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)
const phoneError = ref<string | undefined>(undefined)
const passwordError = ref<string | undefined>(undefined)

/** 成功提示停留多久再跳。够看清，又不至于让人等 */
const SUCCESS_DWELL_MS = 700

/**
 * 刻意不在前端写手机号正则。
 *
 * 后端 LoginRequest 的注释说得很清楚：格式校验在 MemberPhoneUtil.normalize 里，
 * 规范化和校验是同一件事的两面。前端再写一条正则就是第二份手机号规则，两份迟早对不上。
 * 这里只做「非空」这种纯交互层面的拦截。
 *
 * 🔴 而且是在【点击之后】拦，不是把按钮置灰。
 * 按钮置灰时用户看不出差哪一项，只知道点不动。
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
    await auth.login(
      { phone: phone.value.trim(), password: password.value, deviceType: 'H5' },
      remember.value,
    )
    succeeded.value = true
    await new Promise((resolve) => setTimeout(resolve, SUCCESS_DWELL_MS))
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
    // 成功时不复位：跳转前按钮应当一直是 loading，否则会闪一下「可点」
    if (!succeeded.value) {
      submitting.value = false
    }
  }
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1 class="page__title">欢迎回来</h1>
      <p class="page__subtitle">输入手机号和密码，继续参与抽奖</p>
    </header>

    <!--
      用 <form> 包起来是为了拿到两件浏览器免费给的东西：
      密码框回车直接提交，以及移动端键盘把回车键变成「前往」。
      自己监听 keyup.enter 做不到后者。
    -->
    <form class="page__form" novalidate @submit.prevent="submit">
      <Field
        v-model="phone"
        icon="phone"
        type="tel"
        placeholder="手机号"
        autocomplete="username"
        :maxlength="11"
        :error="phoneError"
      />
      <Field
        v-model="password"
        icon="lock"
        type="password"
        placeholder="密码"
        autocomplete="current-password"
        :error="passwordError"
      />

      <p v-if="errorMessage !== ''" class="page__error" role="alert">
        {{ errorMessage }}
        <!-- traceId 一定要露出来：用户截图报障时，凭它一次定位到服务端日志 -->
        <span v-if="errorTraceId !== null" class="page__trace">（编号 {{ errorTraceId }}）</span>
      </p>

      <Button type="submit" :loading="submitting" class="page__submit">登录</Button>

      <div class="page__options">
        <Checkbox v-model="remember" label="记住我" />
        <!--
          忘记密码还没有页面。做成灰色不可点，而不是给一个点了没反应的蓝色链接——
          后者是在骗用户，他会一直点。等找回流程做完再放开
        -->
        <span class="page__forgot" aria-disabled="true">忘记密码？</span>
      </div>
    </form>

    <p class="page__alt">
      还没有账号？<RouterLink class="page__link" :to="{ name: 'register', query: route.query }">
        立即注册
      </RouterLink>
    </p>

    <Result :open="succeeded" text="登录成功" />
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

/* 编号退到最后，但一直在 */
.page__trace {
  color: var(--sv-text-placeholder);
}

.page__submit {
  margin-top: var(--sv-space-sm);
}

.page__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sv-space-xs);
}

.page__forgot {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
}

/*
 * 次要出口就是一行小字，钉在页面底部。
 * margin-top: auto 把中间的空白全部留给表单，短屏时它自然上移，不会盖住内容。
 */
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
