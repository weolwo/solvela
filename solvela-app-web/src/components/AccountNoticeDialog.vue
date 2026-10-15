<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { dismissAccountNotice, useAccountNoticeState } from '@/composables/useAccountNotice'

/**
 * 账号状态挡住了这次操作时的提示弹窗。三种，共用一个壳：
 * <ul>
 *   <li><b>资产出口冻结</b>（点过「这不是我」）→ [去重置密码]；</li>
 *   <li><b>没绑联系方式</b>（要二次验证却没有邮箱）→ [去绑定]；</li>
 *   <li><b>新绑的还在冷静期</b> → 只有 [知道了]。</li>
 * </ul>
 *
 * <h3>为什么要一个弹窗，而不是让页面展示那句错误</h3>
 * 这三档都不是「再试一次就好」：**用户必须去做另一件事**（或者只能等）。
 * 只在兑换页底下显示一行红字的话，用户会反复点那颗按钮 ——
 * 而他永远点不动，也不知道该去哪儿。
 *
 * <h3>🔴 冷静期那一档刻意【没有】「重新绑定」</h3>
 * 再绑一次只会再冷静 7 天。给这颗按钮等于让用户在一个死循环里打转。
 * 真正的出路是「在你常用的设备上操作」，所以文案要说这一句。
 *
 * <h3>🔴 「去绑定」这颗按钮是有代价的</h3>
 * 首次绑定不要求任何额外验证，所以它本身就是一条账号接管链的入口
 * （绑自己的邮箱 → 用它通过二次验证 → 把东西搬走）。
 * 它能存在，前提是服务端那边的 7 天冷静期一起上了 —— 两者缺一不可。
 */
const { current } = useAccountNoticeState()
const router = useRouter()
const route = useRoute()

const TITLES: Record<string, string> = {
  ASSET_OUT_FROZEN: '账号处于保护状态',
  CONTACT_BIND_REQUIRED: '需要先绑定邮箱',
  CONTACT_COOLING: '新绑定的联系方式暂不可用',
}

const HINTS: Record<string, string> = {
  ASSET_OUT_FROZEN:
    '你举报过一次可疑登录，所以兑换、加收货地址、充话费被暂时停用了。重置密码即可恢复。',
  CONTACT_BIND_REQUIRED: '这个操作需要先验证一次邮箱验证码，所以要先给账号绑定一个邮箱。',
}

const title = computed(() => (current.value === null ? '' : (TITLES[current.value.code] ?? '')))

const hint = computed(() => {
  const notice = current.value
  if (notice === null) {
    return ''
  }
  if (notice.code !== 'CONTACT_COOLING') {
    return HINTS[notice.code] ?? ''
  }
  // 拿不到日期就只说「暂时」，不编一个出来
  if (notice.availableAt === null || notice.availableAt === undefined) {
    return '为了账号安全，刚绑定的联系方式要过几天才能用于安全验证。你也可以在常用的设备上操作。'
  }
  const d = new Date(notice.availableAt)
  return `为了账号安全，它要到 ${d.getMonth() + 1} 月 ${d.getDate()} 日之后才能用于安全验证。你也可以在常用的设备上操作。`
})

async function goPasswordReset(): Promise<void> {
  dismissAccountNotice()
  await router.push({ name: 'password-reset' })
}

/**
 * 去绑定。带上 `redirect`：绑完回到用户原来那一页。
 *
 * 🔴 回来之后**不自动重试**原请求 —— 中间隔了一次页面跳转，
 * 让用户自己再点一次，否则他会看到一个自己没点过的操作成功了。
 */
async function goBind(): Promise<void> {
  const target = route.fullPath
  dismissAccountNotice()
  await router.push({ name: 'email-bind', query: { redirect: target } })
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="current !== null"
      class="mask"
      role="alertdialog"
      aria-modal="true"
      :aria-label="title"
    >
      <div class="dialog">
        <h2 class="dialog__title">{{ title }}</h2>
        <p class="dialog__message">{{ current.message }}</p>
        <p v-if="hint !== ''" class="dialog__hint">{{ hint }}</p>

        <div class="dialog__actions">
          <Button
            v-if="current.code === 'ASSET_OUT_FROZEN' && current.unlock === 'PASSWORD_RESET'"
            @click="goPasswordReset"
          >
            去重置密码
          </Button>
          <Button
            v-else-if="current.code === 'CONTACT_BIND_REQUIRED' && current.need === 'EMAIL'"
            @click="goBind"
          >
            去绑定邮箱
          </Button>
          <Button variant="text" @click="dismissAccountNotice">知道了</Button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sv-space-page);
  background: rgb(0 0 0 / 45%);
}

.dialog {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  width: 100%;
  max-width: 340px;
  padding: var(--sv-space-lg);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  box-shadow: var(--sv-shadow-overlay);
}

.dialog__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  line-height: 1.4;
  color: var(--sv-text-primary);
}

.dialog__message {
  margin: 0;
  font-size: var(--sv-font-body);
  line-height: 1.7;
  color: var(--sv-text-primary);
}

.dialog__hint {
  margin: 0;
  font-size: var(--sv-font-caption);
  line-height: 1.6;
  color: var(--sv-text-secondary);
}

.dialog__actions {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-sm);
}
</style>
