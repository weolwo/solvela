<script setup lang="ts">
import { useRouter } from 'vue-router'

import { dismissAccountNotice, useAccountNoticeState } from '@/composables/useAccountNotice'

/**
 * 账号保护状态的提示弹窗。目前只有一种：点过「这不是我」之后资产出口被冻结。
 *
 * <h3>为什么要一个弹窗，而不是让页面展示那句错误</h3>
 * 这一档和其它失败不同：**用户必须去做另一件事**才能恢复（重置密码）。
 * 只在兑换页底下显示一行红字「账号处于保护状态」的话，用户会反复点那颗兑换按钮 ——
 * 而他永远点不动，也不知道该去哪儿。
 *
 * <h3>🔴 只给「去重置密码」，不给「联系客服」</h3>
 * 这件事用户自己五分钟就能办完，而客服要花半天核身。
 * 把客服摆在同一层，一半的人会选那条更慢的路。
 */
const { current } = useAccountNoticeState()
const router = useRouter()

async function goPasswordReset(): Promise<void> {
  dismissAccountNotice()
  await router.push({ name: 'password-reset' })
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="current !== null"
      class="mask"
      role="alertdialog"
      aria-modal="true"
      aria-label="账号保护状态"
    >
      <div class="dialog">
        <h2 class="dialog__title">账号处于保护状态</h2>
        <p class="dialog__message">{{ current.message }}</p>
        <p class="dialog__hint">
          你举报过一次可疑登录，所以兑换、加收货地址、充话费被暂时停用了。重置密码即可恢复。
        </p>

        <div class="dialog__actions">
          <Button v-if="current.unlock === 'PASSWORD_RESET'" @click="goPasswordReset">
            去重置密码
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
