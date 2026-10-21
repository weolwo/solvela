<script setup lang="ts">
/**
 * 设置页。
 *
 * <h3>它终于有一项真实内容了</h3>
 * 之前是空壳（「我的」页上的「设置」必须指向一个真实存在的页面，否则用户会掉进
 * 404 —— 与本项目在别处坚持的「不给死链接」是同一条原则）。
 * 2026-09-10 加了「登录设备」，那是第一项。
 *
 * <p>这里刻意<b>没有</b>用 Section：它封装的是「加载/出错/空」三态，
 * 而这一页是一张写死的清单，不会加载、不会出错、不会为空。
 * 硬套的话要传四个恒定值的 prop，读的人还要停下来确认它们是不是真的恒定。
 *
 * <h3>添加到桌面</h3>
 * 能弹系统安装框的浏览器（Chrome / Edge）点了直接弹；其余的弹一张说明，教用户自己从浏览器菜单里装。
 * 判断逻辑在 useInstallApp，这里只管「每种情况说什么」。
 */
import { ref } from 'vue'

import { useInstallApp, type InstallMode } from '@/composables/useInstallApp'

const { mode, install } = useInstallApp()
const guideOpen = ref(false)

/** 除 prompt 与 installed 以外，每种情况给用户看的步骤 */
const GUIDES: Record<Exclude<InstallMode, 'prompt' | 'installed'>, string[]> = {
  ios: [
    '点浏览器的「分享」按钮（Safari 在底部工具栏，新版在「···」菜单里；Chrome 在地址栏右侧）',
    '在弹出的列表里找到「添加到主屏幕」',
    '点右上角「添加」，桌面上就有 Solvela 的图标了',
  ],
  'in-app': [
    '当前是在 App 里打开的网页，这里没法添加到桌面',
    '点右上角「···」，选「在浏览器中打开」',
    '到浏览器里再进「设置 → 添加到桌面」',
  ],
  manual: [
    '打开浏览器菜单，找「添加到主屏幕」或「安装应用」',
    '找不到这一项的话，说明这个浏览器不支持，换 Chrome 或 Edge 打开本页再试',
  ],
}

async function onInstall(): Promise<void> {
  if (mode.value === 'installed') {
    return
  }
  if (mode.value === 'prompt') {
    await install()
    return
  }
  guideOpen.value = true
}
</script>

<template>
  <div class="page">
    <NavBar title="设置" />

    <div class="page__body">
      <h2 class="page__group">账号安全</h2>
      <div class="page__card">
        <Cell icon="phone" title="手机号" :to="{ name: 'phone-bind' }" />
        <Cell icon="user" title="邮箱" :to="{ name: 'email-bind' }" />
        <Cell icon="lock" title="登录设备" :to="{ name: 'sessions' }" />
        <!--
          消息设置：只有交易和活动两个开关。
          🔴 没有「系统通知」那一档 —— 账号被冻结这种事不该能被静音，
          服务端的契约里压根没有那个字段，所以这里也画不出那个开关。
        -->
        <Cell icon="settings" title="消息设置" :to="{ name: 'notification-preference' }" />
      </div>

      <h2 class="page__group">应用</h2>
      <div class="page__card">
        <Cell
          icon="download"
          title="添加到桌面"
          :value="mode === 'installed' ? '已添加' : undefined"
          :arrow="mode !== 'installed'"
          @click="onInstall"
        />
      </div>
    </div>

    <Sheet v-model="guideOpen" title="添加到桌面">
      <ol v-if="mode !== 'prompt' && mode !== 'installed'" class="guide">
        <li v-for="step in GUIDES[mode]" :key="step" class="guide__step">{{ step }}</li>
      </ol>
    </Sheet>
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
  padding: var(--sv-space-page);
}

.page__group {
  margin: 0 0 var(--sv-space-sm);
  padding-left: var(--sv-space-xs);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  font-weight: 500;
}

.page__card {
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  overflow: hidden;
}

.page__card + .page__group {
  margin-top: var(--sv-space-lg);
}

.guide {
  margin: 0;
  padding-left: 1.4em;
  color: var(--sv-text-primary);
  font-size: 14px;
  line-height: 1.7;
}

.guide__step + .guide__step {
  margin-top: var(--sv-space-sm);
}
</style>
