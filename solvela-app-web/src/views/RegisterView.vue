<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { sendEmailCode, sendSmsCode } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { useCodeSender } from '@/composables/useCodeSender'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/**
 * 用手机号还是邮箱注册。
 *
 * <h3>🔴 邮箱注册可以不设密码，那不是省事，是另一种账号</h3>
 * 后端 {@code MemberRegisterCmd} 允许 EMAIL_CODE 注册时 password 为空
 *（`t_member.password` 可为 NULL，DDL 注释写着「验证码登录可为空」）。
 * 那种会员之后<b>只能靠邮箱验证码登录</b> —— 所以登录页必须同时支持验证码登录，
 * 否则这条注册通道会把人注册进一个自己进不去的账号。
 */
const IDENTITY_OPTIONS = [
  { value: 'phone', label: '手机号' },
  { value: 'email', label: '邮箱' },
] as const
type IdentityKind = (typeof IDENTITY_OPTIONS)[number]['value']

const identityKind = ref<IdentityKind>('phone')

const phone = ref('')
const smsCode = ref('')
const email = ref('')
const emailCode = ref('')
const emailError = ref<string | undefined>(undefined)
const emailCodeError = ref<string | undefined>(undefined)
/** 邮箱已被注册时立起来，与 phoneTaken 同一个用途 */
const emailTaken = ref(false)
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)
/** 手机号已被注册时立起来：此时错误区要多给一个「去登录」的出口，光一行红字没用 */
const phoneTaken = ref(false)

const phoneError = ref<string | undefined>(undefined)
const smsCodeError = ref<string | undefined>(undefined)
const passwordError = ref<string | undefined>(undefined)
const confirmError = ref<string | undefined>(undefined)

/**
 * 「获取验证码」。倒计时、发送中、失败提示全在 useCodeSender 里。
 *
 * 🔴 手机号没填时**在这里拦掉**，不发请求。短信是要花钱的接口，
 * 让一个空号码走一趟真实请求去换一句「格式不正确」没有道理。
 */
const codeSender = useCodeSender(() => sendSmsCode('REGISTER', phone.value.trim()), {
  precheck: () => {
    if (phone.value.trim() === '') {
      // 提示挂在【手机号】那一栏上，不是验证码那一栏 —— 要填的是手机号
      phoneError.value = '请先输入手机号'
      return false
    }
    return true
  },
})

/**
 * 邮箱那条的「获取验证码」。与短信那条各自独立 ——
 * 共用一个的话，切换注册方式时倒计时会跟着串过去，
 * 而那两条码是发到两个完全不同的地方的。
 */
const emailCodeSender = useCodeSender(() => sendEmailCode('REGISTER', email.value.trim()), {
  precheck: () => {
    if (email.value.trim() === '') {
      emailError.value = '请先输入邮箱'
      return false
    }
    return true
  },
})

watch(email, () => {
  emailCode.value = ''
  emailCodeError.value = undefined
  emailError.value = undefined
  emailTaken.value = false
  emailCodeSender.reset()
})

/*
 * 切换注册方式时清掉整体错误。
 * 不清的话，用户被「该手机号已注册」挡下之后切到邮箱，那句红字还挂在下面 ——
 * 而它说的是另一件事。
 */
watch(identityKind, () => {
  errorMessage.value = ''
  errorTraceId.value = null
  phoneTaken.value = false
  emailTaken.value = false
})

/*
 * 换了手机号，之前那个码就是发给别人的了 —— 状态必须跟着清。
 * 不清的话：用户给 A 号发了码，改成 B 号，那个码还躺在框里，
 * 提交时报「验证码错误」，而他明明刚收到过一条。
 */
watch(phone, () => {
  smsCode.value = ''
  smsCodeError.value = undefined
  /*
   * 手机号那一栏的错也要清。不清的话，用户按提示补上号码、成功拿到验证码之后，
   * 「请先输入手机号」还挂在框下面 —— 他刚照做完，页面却还在指责他。
   * 这条错在下一次 validate() 时才会被覆盖，也就是要等到他点提交。
   */
  phoneError.value = undefined
  phoneTaken.value = false
  codeSender.reset()
})

/** 文案，不是校验。权威规则在后端 MemberPasswordPolicy */
const PASSWORD_HINT = '8-32 位，需同时包含字母和数字'

/** 邮箱注册可以不设密码，那时提示要说清楚「不填会怎样」，而不是只说规则 */
const EMAIL_PASSWORD_HINT = '不填也可以，之后用邮箱验证码登录'

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
  const byEmail = identityKind.value === 'email'

  phoneError.value = !byEmail && phone.value.trim() === '' ? '请输入手机号' : undefined
  smsCodeError.value = !byEmail && smsCode.value.trim() === '' ? '请输入验证码' : undefined
  emailError.value = byEmail && email.value.trim() === '' ? '请输入邮箱' : undefined
  emailCodeError.value = byEmail && emailCode.value.trim() === '' ? '请输入验证码' : undefined

  /*
   * 🔴 邮箱注册时密码【可以不填】—— 那种会员之后走验证码登录。
   * 但只要填了就得两次一致：填了一个又打错第二遍，静默忽略比报错更糟。
   */
  const wantsPassword = !byEmail || password.value !== '' || confirmPassword.value !== ''
  passwordError.value = !byEmail && password.value === '' ? '请设置密码' : undefined

  if (!wantsPassword) {
    confirmError.value = undefined
  } else if (confirmPassword.value === '') {
    confirmError.value = '请再次输入密码'
  } else if (confirmPassword.value !== password.value) {
    confirmError.value = '两次输入的密码不一致'
  } else {
    confirmError.value = undefined
  }

  return (
    phoneError.value === undefined &&
    smsCodeError.value === undefined &&
    emailError.value === undefined &&
    emailCodeError.value === undefined &&
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
  emailTaken.value = false
  smsCodeError.value = undefined
  emailCodeError.value = undefined
  if (!validate()) {
    return
  }

  const byEmail = identityKind.value === 'email'
  submitting.value = true
  try {
    await auth.register({
      registerType: byEmail ? 'EMAIL_CODE' : 'PHONE_PASSWORD',
      identity: (byEmail ? email.value : phone.value).trim(),
      // 只带这条通道自己的码。两条都带的话，服务端按 registerType 只看其中一个，
      // 而另一条码会被白白消费掉（验过即作废）
      ...(byEmail ? { emailCode: emailCode.value.trim() } : { smsCode: smsCode.value.trim() }),
      // 🔴 邮箱注册允许不设密码：传空串会被后端当成「填了一个弱密码」而拒掉，
      //    必须是 undefined
      password: password.value === '' ? undefined : password.value,
      deviceType: 'H5',
    })
    // 注册即登录：后端把令牌一起返回了，直接进目标页，不再跳一次登录
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 'CONFLICT') {
        // 409：这个身份已经有主了。挂到对应字段上，并在下面给一个「去登录」的出口
        if (byEmail) {
          emailTaken.value = true
          emailError.value = error.message
        } else {
          phoneTaken.value = true
          phoneError.value = error.message
        }
      } else if (error.code === 'BAD_CREDENTIALS') {
        /*
         * 401 在注册这条路上只有一个来源：验证码不对/已失效/错太多次。
         * 挂到验证码框上，而不是丢进表单级错误区 —— 用户要知道该重填哪一栏，
         * 而「验证码错误」这四个字放在最下面时，他往往先去检查手机号
         */
        if (byEmail) {
          emailCodeError.value = error.message
        } else {
          smsCodeError.value = error.message
        }
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
      <p class="page__subtitle">注册完直接开抽</p>
    </header>

    <form class="page__form" novalidate @submit.prevent="submit">
      <!--
        两条通道摆在明面上。做成下拉框的话，用户得先点开才知道还能用邮箱注册 ——
        而「原来可以不用手机号」正是这一栏要告诉他的事。
      -->
      <Segmented v-model="identityKind" :options="IDENTITY_OPTIONS" />

      <template v-if="identityKind === 'email'">
        <Field
          v-model="email"
          icon="user"
          label="邮箱"
          placeholder="邮箱"
          autocomplete="username"
          :error="emailError"
        />
        <Field
          v-model="emailCode"
          icon="lock"
          type="tel"
          placeholder="邮箱验证码"
          autocomplete="one-time-code"
          :maxlength="6"
          :error="emailCodeError ?? emailCodeSender.error.value"
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
          v-model="smsCode"
          icon="lock"
          type="tel"
          placeholder="短信验证码"
          autocomplete="one-time-code"
          :maxlength="6"
          :error="smsCodeError ?? codeSender.error.value"
        >
          <!--
          按钮放进输入框内部，而不是并排两个控件：并排时两者宽度要靠 flex 分，
          在窄屏上验证码框会被挤到只剩四五个字符宽。
        -->
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
      </template>

      <Field
        v-model="password"
        icon="lock"
        type="password"
        label="密码"
        :placeholder="identityKind === 'email' ? '设置密码（可不填）' : '设置密码'"
        autocomplete="new-password"
        :hint="identityKind === 'email' ? EMAIL_PASSWORD_HINT : PASSWORD_HINT"
        :error="passwordError"
      />
      <Field
        v-model="confirmPassword"
        icon="lock"
        type="password"
        label="确认密码"
        placeholder="再次输入密码"
        autocomplete="new-password"
        :error="confirmError"
      />

      <p v-if="errorMessage !== ''" class="page__error" role="alert">
        {{ errorMessage }}
        <!-- traceId 一定要露出来：用户截图报障时，凭它一次定位到服务端日志 -->
        <span v-if="errorTraceId !== null" class="page__trace">（编号 {{ errorTraceId }}）</span>
      </p>

      <Button type="submit" :loading="submitting" class="page__submit">注册</Button>

      <!-- 身份已被占用是唯一一种「用户下一步很明确」的失败，给个直达按钮比让他找返回键强 -->
      <Button v-if="phoneTaken || emailTaken" variant="text" @click="goLogin">
        {{ phoneTaken ? '该手机号已注册，去登录' : '该邮箱已注册，去登录' }}
      </Button>
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
