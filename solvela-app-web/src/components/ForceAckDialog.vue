<script setup lang="ts">
import { ref } from 'vue'

import { type AnnouncementItem, ackAnnouncement, fetchPendingAck } from '@/api/notification'

/**
 * 强制确认公告的弹窗。
 *
 * <h3>它是整个消息中心里唯一一个「广播但要写到人」的东西</h3>
 * 普通公告的已读只是推一个游标，零额外行数。而这一类要留证据：
 * 谁在什么时候点了「我已阅读并知悉」—— 服务条款变更、停机维护这类场景下是刚需。
 *
 * <p>但服务端<b>只记确认过的</b>，不会给全人群预建「待确认」行。
 * 所以它答得了「谁确认了」，答不了「谁还没确认」。
 *
 * <h3>🔴 不需要「谁还没确认」的名单，因为这个弹窗自带催办</h3>
 * 没确认的话，服务端每次都会把这条公告返回来，弹窗就再弹一次。
 * 所以用户关掉、杀进程、换设备都逃不掉，运营也不用去催。
 *
 * <p>正因如此，这里<b>刻意不提供关闭按钮</b> —— 关了也会立刻再弹，
 * 给一个关不掉的关闭按钮只会让人以为是 bug。
 *
 * <h3>一次只弹一条</h3>
 * 同时有多条待确认时排队弹。堆在一个弹窗里让用户一次点「全部同意」，
 * 那份留痕的说服力就没了。
 */

const pending = ref<AnnouncementItem[]>([])
const submitting = ref(false)

const current = () => pending.value[0]

async function load() {
  try {
    pending.value = await fetchPendingAck()
  } catch {
    // 查不到就不弹。🔴 绝不能反过来「查失败就拦住用户」——
    // 那会让一次接口抖动变成整个 App 打不开
    pending.value = []
  }
}

async function confirm() {
  const item = current()
  if (item === undefined || submitting.value) {
    return
  }
  submitting.value = true
  try {
    await ackAnnouncement(item.id)
    pending.value = pending.value.slice(1)
  } finally {
    submitting.value = false
  }
}

// 不 await：弹窗是旁路，拉不到就不弹，不该阻塞页面渲染
void load()

defineExpose({ reload: load })
</script>

<template>
  <!--
    role="alertdialog" 而不是 dialog：这是需要用户响应才能继续的东西，
    读屏软件会据此改变播报方式。
  -->
  <div
    v-if="pending.length > 0"
    class="mask"
    role="alertdialog"
    aria-modal="true"
    :aria-label="current()?.title"
  >
    <div class="dialog">
      <h2 class="dialog__title">{{ current()?.title }}</h2>

      <div class="dialog__body">
        <p class="dialog__content">{{ current()?.content }}</p>
      </div>

      <!-- 还剩几条时说清楚，否则用户点完一个又冒一个会觉得关不掉 -->
      <p v-if="pending.length > 1" class="dialog__rest">还有 {{ pending.length - 1 }} 条需要确认</p>

      <Button :loading="submitting" @click="confirm">我已阅读并知悉</Button>
    </div>
  </div>
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
  gap: var(--sv-space-md);
  width: 100%;
  max-width: 340px;
  max-height: 80vh;
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

/* 正文可能很长（服务条款），单独滚动 —— 让弹窗整体滚会把按钮也滚走 */
.dialog__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.dialog__content {
  margin: 0;
  font-size: var(--sv-font-body);
  line-height: 1.7;
  color: var(--sv-text-secondary);
  white-space: pre-wrap;
}

.dialog__rest {
  margin: 0;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-placeholder);
  text-align: center;
}
</style>
