<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { sendEmailCode, sendLoginChallengeCode, type LoginType } from '@/api/auth'
import { ApiError, type LoginAlternative, type VerifyChannel } from '@/api/errors'
// src/components 下的组件不走自动注册（只有 src/ui 走），要显式 import
import LoginBlockedDialog from '@/components/LoginBlockedDialog.vue'
import { useCodeSender } from '@/composables/useCodeSender'
import { useCountdown } from '@/composables/useCountdown'
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
 * 这台设备处在观察档，本次登录要多验一道验证码。
 *
 * 🔴 **不是登录失败**：密码已经验过了，只是还差一步。所以这一栏是
 * 「冒出来的第二步」，而不是把用户退回去重来。
 *
 * <h3>凭票，而不是带着码再登一次</h3>
 * 服务端验对密码后发一张 5 分钟的凭票（在错误响应的 details 里），
 * 之后发码、验码都只认它：码发到哪由票决定（手机号登录发短信，邮箱登录发邮件），
 * 验码通过就直接登录成功 —— 不用、也不能再交一次密码。
 */
const challengeRequired = ref(false)
const challengeTicket = ref<string | null>(null)
/**
 * 码走哪条通道，由服务端告诉我们。
 *
 * 🔴 **不按登录方式猜**：手机号登录也可能把码发到邮箱 —— 服务端的核身路由会故意换通道
 * （避开可能已经一起泄露的那个因子），短信没接通时也会退回邮箱。
 * 猜错的表现是让用户去翻一个空的收件箱，然后以为我们没发。
 */
const challengeChannel = ref<VerifyChannel | null>(null)
/** 码寄到了哪（打过码的），展示在验证码栏下面 */
const challengeTarget = ref<string | null>(null)
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
  lastChanceHint.value = ''
  resetChallenge()
  emailCodeSender.reset()
})

watch(email, () => {
  emailCode.value = ''
  emailError.value = undefined
  emailCodeSender.reset()
})

/**
 * 二次验证的「获取验证码」：凭票要码。
 *
 * 🔴 以前这里直接调匿名的短信发码接口，而且只认手机号 —— 邮箱登录的用户走到这一步会被告知
 * 「请先输入手机号」，永远过不去。现在码发到哪由票决定，两种登录方式都通。
 */
const codeSender = useCodeSender(async () => {
  if (challengeTicket.value === null) {
    return
  }
  challengeTarget.value = await sendLoginChallengeCode(challengeTicket.value)
})

/** 这一轮二次验证作废：票、码、提示全清掉，回到「输完密码点登录」那一步 */
function resetChallenge(): void {
  challengeRequired.value = false
  challengeTicket.value = null
  challengeChannel.value = null
  challengeTarget.value = null
  verificationCode.value = ''
  verificationError.value = undefined
  codeSender.reset()
}

/*
 * 换了手机号，这一整轮二次验证就作废了 —— 那道码是发给上一个号的。
 * 不收起来的话，用户会拿着 A 号的码去登 B 号，得到一句「验证码错误」，
 * 而他完全不知道自己错在哪。
 */
watch(phone, () => {
  resetChallenge()
  phoneError.value = undefined
})

/**
 * 「还要等多久」。密码冷却的弹窗和被限流时的登录按钮共用一个 —— 两者不会同时出现
 * （服务端一次只给一个结论），而共用让「倒计时走完要不要放开按钮」只有一处判断。
 */
const wait = useCountdown()

/** 冷却弹窗 */
const cooldownOpen = ref(false)
const cooldownAlternatives = ref<LoginAlternative[]>([])

/** 没有任何核身通道：用户自己做不了任何事，只能找客服 */
const verifyUnavailableOpen = ref(false)

/**
 * 「再错 1 次就要暂停密码登录了」。
 *
 * 服务端**只在剩最后一次时**才给这个数，所以它一出现就该说出来 ——
 * 它能让一个还在回忆密码的人停下来，而那正是我们希望他做的事。
 */
const lastChanceHint = ref('')

/** 倒计时没走完就不让再点，省掉一次必然失败的请求 */
const submitDisabled = computed(() => wait.active.value)

/**
 * 验证码栏下面那句提示。
 *
 * 发出去之后说「已发送到 a***@x.com」—— 打过码的目标本身就告诉了用户去哪儿找。
 * 还没发出去时退回到按通道说「会发到你的邮箱 / 手机」：
 * 🔴 这一句不能按登录方式写，服务端会换通道（手机号登录、码发邮箱）。
 */
const challengeHint = computed(() => {
  if (challengeTarget.value !== null) {
    return `验证码已发送到 ${challengeTarget.value}`
  }
  if (challengeChannel.value === null) {
    return undefined
  }
  return challengeChannel.value === 'SMS' ? '验证码会发到你的手机' : '验证码会发到你的邮箱'
})

function startCooldown(error: ApiError): void {
  const cooldown = error.passwordCooldown
  if (cooldown === null) {
    return
  }
  wait.start(cooldown.retryAfterSeconds)
  cooldownAlternatives.value = cooldown.alternatives
  cooldownOpen.value = true
}

/** 弹窗里点「用验证码登录」：切到邮箱验证码那一档，并把密码清掉 */
function switchToCodeLogin(): void {
  cooldownOpen.value = false
  loginType.value = 'EMAIL_CODE'
  emailUsesPassword.value = false
  password.value = ''
  /*
   * 🔴 倒计时要停掉：被冷却的只有**密码**这条通道，验证码登录压根没被锁。
   * 留着倒计时的话，用户照我们说的切过来了，却发现按钮还是点不动。
   */
  wait.stop()
}

async function goPasswordReset(): Promise<void> {
  cooldownOpen.value = false
  await router.push({ name: 'password-reset' })
}

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
  lastChanceHint.value = ''
  if (!validate()) {
    return
  }
  if (challengeRequired.value && verificationCode.value.trim() === '') {
    verificationError.value = '请输入验证码'
    return
  }

  submitting.value = true
  try {
    if (challengeRequired.value && challengeTicket.value !== null) {
      // 第二步：凭票验码，通过即登录成功 —— 不再交密码
      await auth.completeLoginChallenge(
        challengeTicket.value,
        verificationCode.value.trim(),
        remember.value,
      )
    } else {
      await auth.login(
        {
          loginType: resolvedLoginType(),
          identity: (byEmail.value ? email.value : phone.value).trim(),
          // credential 装什么由 loginType 决定：密码，或者那条邮箱验证码
          credential:
            byEmail.value && !emailUsesPassword.value ? emailCode.value.trim() : password.value,
          deviceType: 'H5',
        },
        remember.value,
      )
    }
    succeeded.value = true
    await new Promise((resolve) => setTimeout(resolve, SUCCESS_DWELL_MS))
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 'DEVICE_VERIFICATION_REQUIRED') {
        /*
         * 🔴 密码是对的，只是这台设备要多验一道。
         * 把验证码那一栏亮出来并自动发码（用户来到这一步只有这一件事要做）——
         * 当成登录失败清空重来的话，用户每次都会走到同一个地方。
         */
        const challenge = error.challenge
        challengeTicket.value = challenge?.ticket ?? null
        challengeChannel.value = challenge?.channel ?? null
        challengeRequired.value = true
        errorMessage.value = error.message
        void codeSender.send()
      } else if (error.code === 'PASSWORD_COOLDOWN') {
        // 密码这条通道被锁了，但人还有出路 —— 弹窗把那几条出路摆出来
        startCooldown(error)
        errorMessage.value = error.message
      } else if (error.code === 'LOGIN_VERIFY_UNAVAILABLE') {
        // 自己做不了任何事。🔴 绝不能在这里给「去绑定」，理由见 errors.ts 里那条码的说明
        verifyUnavailableOpen.value = true
        errorMessage.value = error.message
      } else if (error.code === 'OPERATION_LIMITED') {
        /*
         * 唯一的出路就是等，所以不弹窗，只把按钮按倒计时置灰。
         * 拿不到秒数时不倒计时 —— 编一个数字出来，走到 0 用户以为能点了，结果又是一次 429。
         */
        wait.start(error.retryAfterSeconds ?? 0)
        errorMessage.value = error.message
      } else if (error.code === 'CHALLENGE_EXPIRED') {
        // 票没了（过期、码错太多次、换了设备）：回到输密码那一步，再点一次登录拿新票
        resetChallenge()
        errorMessage.value = error.message
      } else if (challengeRequired.value && error.code === 'BAD_CREDENTIALS') {
        // 已经进到二次验证这一步了，此时的 401 说的是【验证码】不对，不是密码
        verificationError.value = error.message
      } else {
        // BAD_CREDENTIALS / ACCOUNT_DISABLED 都在这里展示后端给的人话。
        // 注意它们里有 401，但拦截器不会把它们当成「掉登录态」，所以能走到这一行。
        errorMessage.value = error.message
        if (error.code === 'BAD_CREDENTIALS' && error.attemptsBeforeCooldown !== null) {
          /*
           * 文案里**不说具体会被冷却多久**：那是阶梯的形状，而用户此刻不需要知道
           * （真被冷却时弹窗会给出准确的倒计时）。只说「还有别的路可走」。
           */
          lastChanceHint.value = '再错一次将暂时停用密码登录，可改用邮箱验证码登录或找回密码。'
        }
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
        placeholder="验证码"
        autocomplete="one-time-code"
        :maxlength="6"
        :hint="challengeHint"
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

      <!-- 最后一次机会的提醒。单独一行、不是红字：它不是报错，是一句劝 -->
      <p v-if="lastChanceHint !== ''" class="page__hint">{{ lastChanceHint }}</p>

      <!--
        倒计时直接写在按钮上，不另起一行：按钮点不动时，用户的眼睛就在按钮上，
        而「为什么点不动」的答案必须出现在同一个地方。
      -->
      <Button type="submit" :loading="submitting" :disabled="submitDisabled" class="page__submit">
        {{ submitDisabled ? `请稍候 ${wait.text.value}` : '登录' }}
      </Button>

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

    <LoginBlockedDialog
      v-model="cooldownOpen"
      title="密码错误次数过多"
      message="为了保护账号，密码登录已暂时停用。你也可以换一种方式进来。"
      :countdown="wait.text.value"
      :alternatives="cooldownAlternatives"
      @code-login="switchToCodeLogin"
      @password-reset="goPasswordReset"
    />

    <!--
      🔴 这个弹窗**只有「知道了」**。此刻没有登录态，对面这个人只证明了「知道密码」——
      给一颗「去绑定邮箱」的按钮，等于让他给别人的账号加一条他自己的找回通道。
      本 App 还没有客服入口，所以指路只能写在文案里。
    -->
    <LoginBlockedDialog
      v-model="verifyUnavailableOpen"
      title="暂时无法验证你的身份"
      message="这个账号目前没有可用于安全验证的手机号或邮箱，请联系客服处理。"
    />
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

/* 劝一句，不是报错 —— 所以不用危险色，否则用户以为又出了一个新问题 */
.page__hint {
  margin: 0;
  padding: 0 var(--sv-space-md);
  color: var(--sv-text-secondary);
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
