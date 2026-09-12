<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { sendEmailCode, sendSmsCode, type LoginType } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { useCodeSender } from '@/composables/useCodeSender'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/**
 * 登录方式。
 *
 * <h3>🔴 邮箱验证码这一条不是锦上添花，是必需的</h3>
 * 邮箱注册允许不设密码（后端 `t_member.password` 可为 NULL，
 * DDL 注释写着「验证码登录可为空」）。没有这条通道的话，
 * 那批会员注册完就再也进不来了。
 *
 * <p>邮箱这一档默认走【验证码】而不是密码，正是因为这个：
 * 默认密码的话，没设过密码的人第一眼看到的是一个他永远填不对的框。
 */
const LOGIN_OPTIONS = [
  { value: 'PHONE_PASSWORD', label: '手机号' },
  { value: 'EMAIL_CODE', label: '邮箱' },
] as const

const loginType = ref<LoginType>('PHONE_PASSWORD')
/** 邮箱那一档里，用验证码还是密码 */
const emailUsesPassword = ref(false)

const phone = ref('')
const email = ref('')
const emailCode = ref('')
const emailError = ref<string | undefined>(undefined)
const password = ref('')

/**
 * 这台设备处在观察档，本次登录要多验一道短信验证码。
 *
 * 🔴 **不是登录失败**：密码已经验过了，只是还差一步。所以这一栏是
 * 「冒出来的第二步」，而不是把用户退回去重来 —— 手机号和密码都保持原样。
 */
const challengeRequired = ref(false)
const verificationCode = ref('')
const verificationError = ref<string | undefined>(undefined)
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

const byEmail = computed(() => loginType.value !== 'PHONE_PASSWORD')

/** 邮箱那一档的「获取验证码」。场景 LOGIN */
const emailCodeSender = useCodeSender(() => sendEmailCode('LOGIN', email.value.trim()), {
  precheck: () => {
    if (email.value.trim() === '') {
      emailError.value = '请先输入邮箱'
      return false
    }
    return true
  },
})

/*
 * 切换登录方式时，把上一档的错和二次验证状态全清掉 ——
 * 它们说的是另一件事，留着只会让人对着一句不相干的红字发愁。
 */
watch([loginType, emailUsesPassword], () => {
  errorMessage.value = ''
  errorTraceId.value = null
  phoneError.value = undefined
  emailError.value = undefined
  passwordError.value = undefined
  challengeRequired.value = false
  verificationCode.value = ''
  verificationError.value = undefined
  emailCodeSender.reset()
})

watch(email, () => {
  emailCode.value = ''
  emailError.value = undefined
  emailCodeSender.reset()
})

/** 二次验证的「获取验证码」。场景是 LOGIN，与注册那条码互不相干 */
const codeSender = useCodeSender(() => sendSmsCode('LOGIN', phone.value.trim()), {
  precheck: () => {
    if (phone.value.trim() === '') {
      phoneError.value = '请先输入手机号'
      return false
    }
    return true
  },
})

/*
 * 换了手机号，这一整轮二次验证就作废了 —— 那道码是发给上一个号的。
 * 不收起来的话，用户会拿着 A 号的码去登 B 号，得到一句「验证码错误」，
 * 而他完全不知道自己错在哪。
 */
watch(phone, () => {
  challengeRequired.value = false
  verificationCode.value = ''
  verificationError.value = undefined
  phoneError.value = undefined
  codeSender.reset()
})

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
  if (!byEmail.value) {
    phoneError.value = phone.value.trim() === '' ? '请输入手机号' : undefined
    passwordError.value = password.value === '' ? '请输入密码' : undefined
    return phoneError.value === undefined && passwordError.value === undefined
  }
  phoneError.value = undefined
  emailError.value = email.value.trim() === '' ? '请输入邮箱' : undefined
  passwordError.value = (
    emailUsesPassword.value ? password.value === '' : emailCode.value.trim() === ''
  )
    ? emailUsesPassword.value
      ? '请输入密码'
      : '请输入验证码'
    : undefined
  return emailError.value === undefined && passwordError.value === undefined
}

/** 三种登录方式最终落成哪一个 */
function resolvedLoginType(): LoginType {
  if (!byEmail.value) {
    return 'PHONE_PASSWORD'
  }
  return emailUsesPassword.value ? 'EMAIL_PASSWORD' : 'EMAIL_CODE'
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  errorMessage.value = ''
  errorTraceId.value = null
  verificationError.value = undefined
  if (!validate()) {
    return
  }
  if (challengeRequired.value && verificationCode.value.trim() === '') {
    verificationError.value = '请输入验证码'
    return
  }

  submitting.value = true
  try {
    await auth.login(
      {
        loginType: resolvedLoginType(),
        identity: (byEmail.value ? email.value : phone.value).trim(),
        // credential 装什么由 loginType 决定：密码，或者那条邮箱验证码
        credential:
          byEmail.value && !emailUsesPassword.value ? emailCode.value.trim() : password.value,
        // 正常设备上这一项永远是 undefined —— 绝大多数登录不受影响
        verificationCode: verificationCode.value.trim() || undefined,
        deviceType: 'H5',
      },
      remember.value,
    )
    succeeded.value = true
    await new Promise((resolve) => setTimeout(resolve, SUCCESS_DWELL_MS))
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 'DEVICE_VERIFICATION_REQUIRED') {
        /*
         * 🔴 密码是对的，只是这台设备要多验一道。
         * 把验证码那一栏亮出来，手机号和密码原样留着 ——
         * 当成登录失败清空重来的话，用户每次都会走到同一个地方。
         */
        challengeRequired.value = true
        errorMessage.value = error.message
      } else if (challengeRequired.value && error.code === 'BAD_CREDENTIALS') {
        // 已经进到二次验证这一步了，此时的 401 说的是【验证码】不对，不是密码
        verificationError.value = error.message
      } else {
        // BAD_CREDENTIALS / ACCOUNT_DISABLED / OPERATION_LIMITED 都在这里展示后端给的人话。
        // 注意它们里有 401，但拦截器不会把它们当成「掉登录态」，所以能走到这一行。
        errorMessage.value = error.message
      }
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
      <p class="page__subtitle">继续参与抽奖</p>
    </header>

    <!--
      用 <form> 包起来是为了拿到两件浏览器免费给的东西：
      密码框回车直接提交，以及移动端键盘把回车键变成「前往」。
      自己监听 keyup.enter 做不到后者。
    -->
    <form class="page__form" novalidate @submit.prevent="submit">
      <Segmented v-model="loginType" :options="LOGIN_OPTIONS" />

      <template v-if="byEmail">
        <Field
          v-model="email"
          icon="user"
          label="邮箱"
          placeholder="邮箱"
          autocomplete="username"
          :error="emailError"
        />
        <!--
          默认走验证码而不是密码：邮箱注册允许不设密码，
          默认密码的话，那批人第一眼看到的是一个自己永远填不对的框。
        -->
        <Field
          v-if="!emailUsesPassword"
          v-model="emailCode"
          icon="lock"
          type="tel"
          placeholder="邮箱验证码"
          autocomplete="one-time-code"
          :maxlength="6"
          :error="passwordError ?? emailCodeSender.error.value"
        >
          <template #suffix>
            <Button
              variant="text"
              type="button"
              :block="false"
              :disabled="!emailCodeSender.canSend.value"
              @click="emailCodeSender.send"
            >
              {{ emailCodeSender.label.value }}
            </Button>
          </template>
        </Field>
        <Field
          v-else
          v-model="password"
          icon="lock"
          type="password"
          label="密码"
          placeholder="密码"
          autocomplete="current-password"
          :error="passwordError"
        />
      </template>

      <template v-else>
        <Field
          v-model="phone"
          icon="phone"
          type="tel"
          label="手机号"
          placeholder="手机号"
          autocomplete="username"
          :maxlength="11"
          :error="phoneError"
        />
        <Field
          v-model="password"
          icon="lock"
          type="password"
          label="密码"
          placeholder="密码"
          autocomplete="current-password"
          :error="passwordError"
        />
      </template>

      <!--
        只在服务端明确要求时才出现。默认就摆在这里的话，
        绝大多数用户会以为每次登录都要验一道码。
      -->
      <Field
        v-if="challengeRequired"
        v-model="verificationCode"
        icon="lock"
        type="tel"
        placeholder="短信验证码"
        autocomplete="one-time-code"
        :maxlength="6"
        :error="verificationError ?? codeSender.error.value"
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

      <p v-if="errorMessage !== ''" class="page__error" role="alert">
        {{ errorMessage }}
        <!-- traceId 一定要露出来：用户截图报障时，凭它一次定位到服务端日志 -->
        <span v-if="errorTraceId !== null" class="page__trace">（编号 {{ errorTraceId }}）</span>
      </p>

      <Button type="submit" :loading="submitting" class="page__submit">登录</Button>

      <!-- 邮箱那一档给一个切换出口：设过密码的人不该被逼着每次都收一封信 -->
      <p v-if="byEmail" class="page__switch">
        <button
          type="button"
          class="page__link-btn"
          @click="emailUsesPassword = !emailUsesPassword"
        >
          {{ emailUsesPassword ? '改用邮箱验证码登录' : '改用密码登录' }}
        </button>
      </p>

      <div class="page__options">
        <Checkbox v-model="remember" label="记住我" />
        <!-- 2026-09-10 放开：找回流程做完了，见 PasswordResetView -->
        <RouterLink class="page__forgot" :to="{ name: 'password-reset' }">忘记密码？</RouterLink>
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

.page__switch {
  margin: 0;
  text-align: center;
}

.page__link-btn {
  border: 0;
  padding: var(--sv-space-xs);
  background: transparent;
  color: var(--sv-color-primary);
  font: inherit;
  font-size: var(--sv-font-caption);
  cursor: pointer;
}

.page__link-btn:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
  border-radius: var(--sv-radius-sm);
}

.page__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sv-space-xs);
}

.page__forgot {
  color: var(--sv-color-primary);
  font-size: var(--sv-font-caption);
  text-decoration: none;
}

.page__forgot:active {
  opacity: 0.6;
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
