<script setup lang="ts">
/**
 * 绑定 / 更换手机号。
 *
 * <h3>它不是「改个资料」，是换掉一条登录身份</h3>
 * 手机号在这个系统里比邮箱重一档：它是注册的默认身份，而
 * `uk_mbr_phone_hash` 是唯一约束 —— 换绑意味着
 * 「原来那个号从此登不了、也注册不了这个账号」。
 *
 * <h3>🔴 换绑要多证明一次「你是原主」，首次绑定不用</h3>
 * 拦的是这条链：**会话被盗 → 换绑成攻击者的号 → 短信验证码登录 → 永久接管**。
 * 首次绑定不需要，因为那时这条链的起点还不存在。
 *
 * <p>与 {@link EmailBindView} 逐段对称。差异只有两处，都是真实存在的：
 * 用手机号的工具校验、码走短信通道（而短信要花钱，服务端的 IP 日限紧得多）。
 */
import { computed, onMounted, ref } from 'vue'

import { bindPhone, fetchContact, sendSmsCode, type MemberContact } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { useCodeSender } from '@/composables/useCodeSender'

/** 成功提示停留多久。够看清，又不至于挡住下一步 */
const SUCCESS_DWELL_MS = 1500

const contact = ref<MemberContact | null>(null)
const loading = ref(true)
const loadError = ref('')

const phone = ref('')
const code = ref('')
const currentPassword = ref('')
/**
 * 旧手机号，用户<b>自己打一遍</b>。
 *
 * 🔴 页面上只有脱敏值（`138****8000`），而发码接口收的是明文 ——
 * 服务端刻意不下发明文，那是整套 PII 加密的前提。
 * 让他自己输入不是将就：那个号本来就是他的，打得出来。
 */
const oldPhone = ref('')
const oldPhoneError = ref<string | undefined>(undefined)
const oldPhoneCode = ref('')

const phoneError = ref<string | undefined>(undefined)
const codeError = ref<string | undefined>(undefined)
const proofError = ref<string | undefined>(undefined)
const errorMessage = ref('')
const errorTraceId = ref<string | null>(null)
const submitting = ref(false)
const succeeded = ref(false)

const rebinding = computed(
  () => contact.value?.phone !== null && contact.value?.phone !== undefined,
)
const canUsePassword = computed(() => contact.value?.passwordSet === true)
const proofByPassword = ref(true)

const newCodeSender = useCodeSender(() => sendSmsCode('BIND', phone.value.trim()), {
  precheck: () => {
    if (phone.value.trim() === '') {
      // 🔴 号码没填就拦掉，不发请求。短信是要花钱的接口
      phoneError.value = '请先输入新手机号'
      return false
    }
    return true
  },
})

const oldCodeSender = useCodeSender(() => sendSmsCode('BIND', oldPhone.value.trim()), {
  precheck: () => {
    if (oldPhone.value.trim() === '') {
      oldPhoneError.value = '请先输入当前绑定的手机号'
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
    proofByPassword.value = contact.value.passwordSet
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

function validate(): boolean {
  phoneError.value = phone.value.trim() === '' ? '请输入新手机号' : undefined
  codeError.value = code.value.trim() === '' ? '请输入验证码' : undefined
  proofError.value = undefined
  oldPhoneError.value = undefined
  if (rebinding.value) {
    if (proofByPassword.value) {
      proofError.value = currentPassword.value === '' ? '请输入当前密码' : undefined
    } else {
      oldPhoneError.value = oldPhone.value.trim() === '' ? '请输入当前绑定的手机号' : undefined
      proofError.value = oldPhoneCode.value.trim() === '' ? '请输入原手机号验证码' : undefined
    }
  }
  return (
    phoneError.value === undefined &&
    codeError.value === undefined &&
    oldPhoneError.value === undefined &&
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
    await bindPhone({
      phone: phone.value.trim(),
      code: code.value.trim(),
      // 只带用上的那一个。两个都带的话，另一个白填
      ...(rebinding.value
        ? proofByPassword.value
          ? { currentPassword: currentPassword.value }
          : { oldPhoneCode: oldPhoneCode.value.trim() }
        : {}),
    })
    succeeded.value = true
    setTimeout(() => (succeeded.value = false), SUCCESS_DWELL_MS)
    await load()
    phone.value = ''
    code.value = ''
    currentPassword.value = ''
    oldPhone.value = ''
    oldPhoneCode.value = ''
    newCodeSender.reset()
    oldCodeSender.reset()
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 'CONFLICT') {
        phoneError.value = error.message
      } else if (error.code === 'BAD_CREDENTIALS') {
        // 这条路上的 401 有两个来源：新号码的码不对，或者原主证明不对。
        // 按「用户此刻在填什么」归位 —— 换绑时更可能是证明那一栏
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
    <NavBar :title="rebinding ? '更换手机号' : '绑定手机号'" />

    <div class="page__body">
      <p v-if="loading" class="state__empty">加载中…</p>

      <div v-else-if="loadError !== ''" class="state">
        <p class="state__error" role="alert">{{ loadError }}</p>
        <Button variant="text" :block="false" @click="load">重试</Button>
      </div>

      <template v-else>
        <div class="current">
          <span class="current__label">当前手机号</span>
          <span class="current__value" :class="{ 'current__value--none': !rebinding }">
            {{ contact?.phone ?? '未绑定' }}
          </span>
        </div>

        <p class="page__intro">绑定后可以用这个手机号登录，也能在换设备时用它验证身份。</p>

        <form class="page__form" novalidate @submit.prevent="submit">
          <Field
            v-model="phone"
            icon="phone"
            type="tel"
            label="新手机号"
            placeholder="新手机号"
            autocomplete="tel"
            :maxlength="11"
            :error="phoneError"
          />
          <Field
            v-model="code"
            icon="lock"
            type="tel"
            placeholder="新手机号收到的验证码"
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

          <template v-if="rebinding">
            <p class="proof__hint">更换手机号需要先证明你是这个账号的主人</p>

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
                v-model="oldPhone"
                icon="phone"
                type="tel"
                label="原手机号"
                placeholder="当前绑定的手机号"
                autocomplete="tel"
                :maxlength="11"
                hint="填你现在绑定的那个号，验证码会发到那里"
                :error="oldPhoneError"
              />
              <Field
                v-model="oldPhoneCode"
                icon="lock"
                type="tel"
                placeholder="原手机号收到的验证码"
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

            <p v-if="canUsePassword" class="proof__switch">
              <button type="button" class="proof__link" @click="proofByPassword = !proofByPassword">
                {{ proofByPassword ? '改用原手机号验证码' : '改用当前密码验证' }}
              </button>
            </p>
            <p v-else class="proof__hint">你还没有设置密码，所以只能通过原手机号验证码更换。</p>
          </template>

          <p v-if="errorMessage !== ''" class="page__error" role="alert">
            {{ errorMessage }}
            <span v-if="errorTraceId !== null" class="page__trace"
              >（编号 {{ errorTraceId }}）</span
            >
          </p>

          <Button type="submit" :loading="submitting">
            {{ rebinding ? '确认更换' : '绑定手机号' }}
          </Button>
        </form>
      </template>
    </div>

    <Result :open="succeeded" text="手机号已更新" />
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
  font-variant-numeric: tabular-nums;
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
