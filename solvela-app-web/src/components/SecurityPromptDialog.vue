<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { answerSecurityPrompt, fetchSecurityPrompts, type SecurityPrompt } from '@/api/security'

/**
 * 「你的账号刚在 XX 登录，是你吗？」
 *
 * <h3>它是整套风控唯一的「答案」来源</h3>
 * 规则给出的是猜测。没有标注，「打扰了多少本人、漏过了多少盗号」只能靠猜，
 * 阈值也就只能靠感觉调。两颗按钮，是我们能拿到的最便宜、最可信的标注。
 *
 * <h3>🔴 两颗按钮刻意不等重</h3>
 * 「是我」是主按钮；「不是我」是次要的文字按钮 + 二次确认 ——
 * 它会让所有设备下线并**冻结兑换、加地址、充话费**。
 * 两颗一样显眼的按钮，会让一半的人随手点错，而点错「不是我」的代价落在他自己身上。
 *
 * <h3>冷启动 + 回到前台各问一次</h3>
 * 服务端那边不推送，所以要主动拉。回到前台也拉一次：盗号往往发生在用户没看手机的那段时间，
 * 而他重新打开 App 的那一刻正是最该问他的时候。
 *
 * <p>⚠️ 拉失败就不弹。🔴 绝不能反过来「拉不到就拦住用户」—— 那会让一次接口抖动变成整个 App 打不开。
 */
const pending = ref<SecurityPrompt[]>([])
const submitting = ref(false)
/** 「不是我」的二次确认 */
const confirmingNotMe = ref(false)
/** 答完「不是我」之后的收尾：必须告诉他还要去改密码 */
const reportedDone = ref(false)

const router = useRouter()

function current(): SecurityPrompt | undefined {
  return pending.value[0]
}

const DEVICE_LABELS: Record<string, string> = {
  APP: '手机 App',
  H5: '手机浏览器',
  WECHAT: '微信',
  PC: '电脑',
}

function deviceLabel(prompt: SecurityPrompt): string {
  if (prompt.deviceType === null) {
    return '未知设备'
  }
  return DEVICE_LABELS[prompt.deviceType] ?? prompt.deviceType
}

function timeLabel(prompt: SecurityPrompt): string {
  if (prompt.loginTime <= 0) {
    return '刚刚'
  }
  const d = new Date(prompt.loginTime)
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load(): Promise<void> {
  try {
    pending.value = await fetchSecurityPrompts()
  } catch {
    // 拉不到就不弹。这里不展示任何错误：用户没有主动做任何事，不该看到一条失败提示
    pending.value = []
  }
}

async function answer(isMe: boolean): Promise<void> {
  const prompt = current()
  if (prompt === undefined || submitting.value) {
    return
  }
  submitting.value = true
  try {
    await answerSecurityPrompt(prompt.promptId, isMe)
    pending.value = pending.value.slice(1)
    confirmingNotMe.value = false
    if (!isMe) {
      reportedDone.value = true
    }
  } catch {
    /*
     * 答不上去（网络断了、单号已失效）就把这一条收起来。
     * 🔴 留在那儿反复让他点的话，用户会以为是自己的操作没生效 ——
     * 而真正重要的那一半（不是我）在服务端失败时什么也没发生，下次回到前台还会再问一遍。
     */
    pending.value = pending.value.slice(1)
    confirmingNotMe.value = false
  } finally {
    submitting.value = false
  }
}

async function goPasswordReset(): Promise<void> {
  reportedDone.value = false
  await router.push({ name: 'password-reset' })
}

function onVisible(): void {
  if (document.visibilityState === 'visible') {
    void load()
  }
}

onMounted(() => {
  void load()
  document.addEventListener('visibilitychange', onVisible)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisible)
})

defineExpose({ reload: load })
</script>

<template>
  <Teleport to="body">
    <div
      v-if="current() !== undefined && !reportedDone"
      class="mask"
      role="alertdialog"
      aria-modal="true"
      aria-label="确认登录"
    >
      <div class="dialog">
        <h2 class="dialog__title">刚才是你登录的吗？</h2>
        <p class="dialog__message">
          你的账号在 <b>{{ timeLabel(current()!) }}</b> 于
          <b>{{ current()!.location ?? '未知地点' }}</b> 的
          <b>{{ deviceLabel(current()!) }}</b> 上登录了。
        </p>

        <!-- 还剩几条时说清楚，否则用户答完一个又冒一个会觉得关不掉 -->
        <p v-if="pending.length > 1" class="dialog__rest">
          还有 {{ pending.length - 1 }} 条需要确认
        </p>

        <template v-if="!confirmingNotMe">
          <div class="dialog__actions">
            <Button :loading="submitting" @click="answer(true)">是我</Button>
            <!--
              次要按钮：它会让所有设备下线并冻结兑换。
              和「是我」一样显眼的话，一半的人会随手点错
            -->
            <Button variant="text" @click="confirmingNotMe = true">不是我</Button>
          </div>
        </template>
        <template v-else>
          <p class="dialog__warn">
            我们会让所有设备下线，并暂时停用兑换、加收货地址、充话费，直到你重置密码。
          </p>
          <div class="dialog__actions">
            <Button variant="danger" :loading="submitting" @click="answer(false)">
              确认，不是我
            </Button>
            <Button variant="text" @click="confirmingNotMe = false">返回</Button>
          </div>
        </template>
      </div>
    </div>

    <!-- 答完「不是我」的收尾：把「接下来该做什么」一次说清，并给一条路 -->
    <div v-if="reportedDone" class="mask" role="alertdialog" aria-modal="true" aria-label="已处理">
      <div class="dialog">
        <h2 class="dialog__title">已处理</h2>
        <p class="dialog__message">
          所有设备已登出。现在请重置密码 —— <b>这也是恢复兑换和充话费的唯一方式</b>。
        </p>
        <div class="dialog__actions">
          <Button @click="goPasswordReset">去重置密码</Button>
          <Button variant="text" @click="reportedDone = false">稍后再说</Button>
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
  color: var(--sv-text-secondary);
}

.dialog__warn {
  margin: 0;
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
  color: var(--sv-color-danger);
}

.dialog__rest {
  margin: 0;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-placeholder);
  text-align: center;
}

.dialog__actions {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-sm);
}
</style>
