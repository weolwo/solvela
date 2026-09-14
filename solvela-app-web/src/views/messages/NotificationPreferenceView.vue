<script setup lang="ts">
import { ref, watch } from 'vue'

import { fetchPreference, savePreference } from '@/api/notification'
import { useAsync } from '@/composables/useAsync'

/**
 * 消息设置（免打扰）。
 *
 * <h3>🔴 只有两个开关，没有「系统通知」那一档</h3>
 * 账号被冻结、服务条款变更这类东西<b>不该能被静音</b>。
 * 服务端的契约（`NotificationPreferenceView`）里压根没有 `systemEnabled` 字段，
 * 所以这里画不出那个开关 —— 这条约束由类型兜底，而不是靠前端记得把它置灰。
 *
 * <h3>没有偏好记录 = 全部开启</h3>
 * 偏好行是<b>懒创建</b>的：用户第一次改设置才落库。所以绝大多数人没有这一行，
 * 服务端查不到时返回「都开着」的默认值。
 *
 * <p>⚠️ 反过来（查不到当成都关了）的后果是全体存量用户再也收不到任何通知，
 * 而且不报错 —— 服务端那边有一条测试专门盯着这件事。
 *
 * <h3>改完立即保存，没有「保存」按钮</h3>
 * 两个开关的设置页放一个保存按钮，用户十有八九会以为拨完就生效了然后直接返回。
 */

const preference = useAsync(fetchPreference)

const tradeEnabled = ref(true)
const marketingEnabled = ref(true)
/** 首帧把服务端值灌进来时不要触发保存，否则一进页面就是一次无意义的写 */
const hydrated = ref(false)
const saving = ref(false)

watch(preference.data, (value) => {
  if (value === null) {
    return
  }
  tradeEnabled.value = value.tradeEnabled
  marketingEnabled.value = value.marketingEnabled
  hydrated.value = true
})

async function persist() {
  if (!hydrated.value || saving.value) {
    return
  }
  saving.value = true
  try {
    await savePreference({
      tradeEnabled: tradeEnabled.value,
      marketingEnabled: marketingEnabled.value,
    })
  } finally {
    saving.value = false
  }
}

watch([tradeEnabled, marketingEnabled], persist)
</script>

<template>
  <div class="page">
    <NavBar title="消息设置" />

    <div class="page__body">
      <Section
        title="接收哪些消息"
        :loading="preference.loading.value"
        :error="preference.error.value"
        :empty="false"
        empty-text=""
        @retry="preference.reload"
      >
        <div class="switches">
          <Checkbox v-model="tradeEnabled" label="交易物流（发货、券即将过期）" />
          <Checkbox v-model="marketingEnabled" label="活动营销（中奖、活动预告）" />
        </div>

        <!--
          把「关不掉的那一类」明写出来，而不是画一个置灰的开关。
          置灰的开关会让人一直去点它，然后怀疑是坏了。
        -->
        <p class="hint">系统通知（账号安全、服务条款变更）关系到账号能否正常使用，将始终发送。</p>
      </Section>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: var(--sv-bg-page);
}

.page__body {
  padding: var(--sv-space-md) var(--sv-space-page) var(--sv-space-xl);
}

.switches {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding: var(--sv-space-lg) var(--sv-space-md);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.hint {
  margin: var(--sv-space-md) 0 0;
  padding: 0 var(--sv-space-xs);
  font-size: var(--sv-font-caption);
  line-height: 1.6;
  color: var(--sv-text-secondary);
}
</style>
