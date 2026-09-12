<script setup lang="ts">
/**
 * 绑定 / 更换邮箱。
 *
 * <h3>它不是「改个资料」，是新增一条登录身份</h3>
 * 绑完之后这个邮箱就能用来登录、能用来重置密码。所以它归「账号安全」，
 * 不归「个人资料」—— 后端把它放在认证契约里也是同一个判断。
 *
 * <h3>🔴 换绑要多证明一次「你是原主」，首次绑定不用</h3>
 * 拦的是这条链：**会话被盗 → 换绑成攻击者的邮箱 → 用「忘记密码」重置 → 永久接管**。
 * 每一步单看都合法，而 C 端令牌有 30 天有效期，token 泄露的机会比密码泄露多得多。
 * 首次绑定不需要，因为那时这条链的起点还不存在。
 *
 * <p>原主证明有两条路，**取决于他有没有设过密码**：
 * 邮箱注册的会员可以没有密码，给他一个「输入当前密码」的框，
 * 是让他对着一个填不了的东西发愁。所以有密码才显示那一栏，
 * 没有就只给「旧邮箱验证码」。
 */
import { computed, onMounted, ref } from 'vue'

import { bindEmail, fetchContact, sendEmailCode, type MemberContact } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { useCodeSender } from '@/composables/useCodeSender'

/** 成功提示停留多久。够看清，又不至于挡住下一步 */
const SUCCESS_DWELL_MS = 1500

const contact = ref<MemberContact | null>(null)
const loading = ref(true)
const loadError = ref('')

const email = ref('')
const code = ref('')
const currentPassword = ref('')
/**
 * 旧邮箱地址，用户<b>自己打一遍</b>。
 *
 * 🔴 页面上只有脱敏值（`a***@example.com`），而发码接口收的是明文 ——
 * 服务端刻意不下发明文，那是整套 PII 加密的前提。
 *
 * <p>让用户自己输入不是将就：他本来就拥有那个邮箱，打得出来；
 * 而打错了只会收不到码，不会泄露任何东西（发码接口对「不是你的邮箱」
 * 会静默成功，见后端 MemberEmailCodeIssuer）。
 */
const oldEmail = ref('')
const oldEmailError = ref<string | undefined>(undefined)
const oldEmailCode = ref('')

const emailError = ref<string | undefined>(undefined)
const codeError = ref<string | undefined>(undefined)
const proofError = ref<string | undefined>(undefined)
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)
const submitting = ref(false)
const succeeded = ref(false)

/** 已经绑过邮箱 = 这次是换绑，要多给一样东西 */
const rebinding = computed(
  () => contact.value?.email !== null && contact.value?.email !== undefined,
)
/** 没设过密码的人只有「旧邮箱验证码」这一条路 */
const canUsePassword = computed(() => contact.value?.passwordSet === true)
/** 换绑时用密码还是旧邮箱验证码。有密码就默认用密码 —— 它不用等一封信 */
const proofByPassword = ref(true)

const newCodeSender = useCodeSender(() => sendEmailCode('BIND', email.value.trim()), {
  precheck: () => {
    if (email.value.trim() === '') {
      emailError.value = '请先输入新邮箱'
      return false
    }
    return true
  },
})

/**
 * 旧邮箱那条码。发到用户自己填的那个地址上。
 *
 * <p>⚠️ 填错了不会有任何提示：发码接口对「不是你的邮箱」是<b>静默成功</b>的
 *（如实回答等于送出一个账号枚举接口）。所以下面那句提示语要说清楚
 * 「填你当前绑定的那个」—— 用户收不到信时才知道该去检查什么。
 */
const oldCodeSender = useCodeSender(() => sendEmailCode('BIND', oldEmail.value.trim()), {
  precheck: () => {
    if (oldEmail.value.trim() === '') {
      oldEmailError.value = '请先输入当前绑定的邮箱'
      return false
    }
    return true
  },
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    contact.value = await fetchContact()
    // 没密码的人只能走旧邮箱那条，默认值跟着实际情况走
    proofByPassword.value = contact.value.passwordSet
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

function validate(): boolean {
  emailError.value = email.value.trim() === '' ? '请输入新邮箱' : undefined
  codeError.value = code.value.trim() === '' ? '请输入验证码' : undefined
  proofError.value = undefined
  oldEmailError.value = undefined
  if (rebinding.value) {
    if (proofByPassword.value) {
      proofError.value = currentPassword.value === '' ? '请输入当前密码' : undefined
    } else {
      oldEmailError.value = oldEmail.value.trim() === '' ? '请输入当前绑定的邮箱' : undefined
      proofError.value = oldEmailCode.value.trim() === '' ? '请输入旧邮箱验证码' : undefined
    }
  }
  return (
    emailError.value === undefined &&
    codeError.value === undefined &&
    oldEmailError.value === undefined &&
    proofError.value === undefined
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
    await bindEmail({
      email: email.value.trim(),
      code: code.value.trim(),
      // 只带用上的那一个。两个都带的话，服务端按顺序取，另一个白填
      ...(rebinding.value
        ? proofByPassword.value
          ? { currentPassword: currentPassword.value }
          : { oldEmailCode: oldEmailCode.value.trim() }
        : {}),
    })
    succeeded.value = true
    // Result 是纯展示组件，没有关闭事件 —— 自己收起来，
    // 不收的话那个成功遮罩会一直盖在页面上
    setTimeout(() => (succeeded.value = false), SUCCESS_DWELL_MS)
    await load()
    email.value = ''
    code.value = ''
    currentPassword.value = ''
    oldEmail.value = ''
    oldEmailCode.value = ''
    newCodeSender.reset()
    oldCodeSender.reset()
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 'CONFLICT') {
        emailError.value = error.message
      } else if (error.code === 'BAD_CREDENTIALS') {
        // 这条路上的 401 有两个来源：新邮箱的码不对，或者原主证明不对。
        // 后端把它们分成了两个 reason，但到这一层只剩一个状态码 ——
        // 所以按「用户此刻在填什么」归位：换绑时更可能是证明那一栏
        if (rebinding.value) {
          proofError.value = error.message
        } else {
          codeError.value = error.message
        }
      } else {
        errorMessage.value = error.message
      }
      errorTraceId.value = error.traceId
    } else {
      errorMessage.value = '操作失败，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <NavBar :title="rebinding ? '更换邮箱' : '绑定邮箱'" />

    <div class="page__body">
      <p v-if="loading" class="state__empty">加载中…</p>

      <div v-else-if="loadError !== ''" class="state">
        <p class="state__error" role="alert">{{ loadError }}</p>
        <Button variant="text" :block="false" @click="load">重试</Button>
      </div>

      <template v-else>
        <!-- 当前状态摆在最前：用户点进来第一个问题就是「我现在绑的是哪个」 -->
        <div class="current">
          <span class="current__label">当前邮箱</span>
          <span class="current__value" :class="{ 'current__value--none': !rebinding }">
            {{ contact?.email ?? '未绑定' }}
          </span>
        </div>

        <p class="page__intro">绑定后可以用这个邮箱登录，也能在忘记密码时用它找回。</p>

        <form class="page__form" novalidate @submit.prevent="submit">
          <Field
            v-model="email"
            icon="user"
            label="新邮箱"
            placeholder="新邮箱"
            autocomplete="email"
            :error="emailError"
          />
          <Field
            v-model="code"
            icon="lock"
            type="tel"
            placeholder="新邮箱收到的验证码"
            autocomplete="one-time-code"
            :maxlength="6"
            :error="codeError ?? newCodeSender.error.value"
          >
            <template #suffix>
              <Button
                variant="text"
                type="button"
                :block="false"
                :disabled="!newCodeSender.canSend.value"
                @click="newCodeSender.send"
              >
                {{ newCodeSender.label.value }}
              </Button>
            </template>
          </Field>

          <!--
            🔴 换绑才要的那一步。首次绑定不显示 —— 那时这条攻击链的起点还不存在，
            多要一样东西只是白白劝退用户。
          -->
          <template v-if="rebinding">
            <p class="proof__hint">换绑需要先证明你是这个账号的主人</p>

            <Field
              v-if="proofByPassword"
              v-model="currentPassword"
              icon="lock"
              type="password"
              label="当前密码"
              placeholder="当前密码"
              autocomplete="current-password"
              :error="proofError"
            />
            <template v-else>
              <Field
                v-model="oldEmail"
                icon="user"
                label="原邮箱"
                placeholder="当前绑定的邮箱"
                autocomplete="email"
                hint="填你现在绑定的那个邮箱，验证码会发到那里"
                :error="oldEmailError"
              />
              <Field
                v-model="oldEmailCode"
                icon="lock"
                type="tel"
                placeholder="旧邮箱收到的验证码"
                autocomplete="one-time-code"
                :maxlength="6"
                :error="proofError ?? oldCodeSender.error.value"
              >
                <template #suffix>
                  <Button
                    variant="text"
                    type="button"
                    :block="false"
                    :disabled="!oldCodeSender.canSend.value"
                    @click="oldCodeSender.send"
                  >
                    {{ oldCodeSender.label.value }}
                  </Button>
                </template>
              </Field>
            </template>

            <!--
              只有真的有第二条路时才给切换。没设过密码的人看到一个
              「改用当前密码验证」的链接，点进去只会得到一个他填不了的框。
            -->
            <p v-if="canUsePassword" class="proof__switch">
              <button type="button" class="proof__link" @click="proofByPassword = !proofByPassword">
                {{ proofByPassword ? '改用旧邮箱验证码' : '改用当前密码验证' }}
              </button>
            </p>
            <p v-else class="proof__hint">你还没有设置密码，所以只能通过旧邮箱验证码换绑。</p>
          </template>

          <p v-if="errorMessage !== ''" class="page__error" role="alert">
            {{ errorMessage }}
            <span v-if="errorTraceId !== null" class="page__trace"
              >（编号 {{ errorTraceId }}）</span
            >
          </p>

          <Button type="submit" :loading="submitting">
            {{ rebinding ? '确认更换' : '绑定邮箱' }}
          </Button>
        </form>
      </template>
    </div>

    <Result :open="succeeded" text="邮箱已更新" />
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

.current {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.current__label {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.current__value {
  font-size: var(--sv-font-body);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current__value--none {
  color: var(--sv-text-placeholder);
  font-weight: 400;
}

.page__intro {
  margin: var(--sv-space-md) 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.5;
}

.page__form {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
}

.proof__hint {
  margin: 0;
  padding: 0 var(--sv-space-md);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.5;
}

.proof__hint--warn {
  color: var(--sv-color-warning);
}

.proof__switch {
  margin: 0;
  text-align: center;
}

.proof__link {
  border: 0;
  padding: var(--sv-space-xs);
  background: transparent;
  color: var(--sv-color-primary);
  font: inherit;
  font-size: var(--sv-font-caption);
  cursor: pointer;
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

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-xl) 0;
}

.state__error {
  margin: 0;
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

.state__empty {
  margin: 0;
  padding: var(--sv-space-xl) 0;
  text-align: center;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
}
</style>
