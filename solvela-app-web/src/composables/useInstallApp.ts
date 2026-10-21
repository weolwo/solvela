import { computed, ref, shallowRef, type ComputedRef } from 'vue'

/**
 * 「添加到桌面」（PWA 安装）。
 *
 * <p>各家浏览器给的能力差得很远，所以这里只做一件事：判断<b>当前这个浏览器</b>该怎么装，
 * 页面按结果给一个按钮或一段说明。
 *
 * <ul>
 *   <li><b>prompt</b>：Chrome / Edge 等发过 `beforeinstallprompt`，点一下就能弹系统的安装框；</li>
 *   <li><b>ios</b>：iOS 没有安装接口，只能教用户点「分享 → 添加到主屏幕」；</li>
 *   <li><b>in-app</b>：微信、QQ、支付宝等 App 的内置浏览器，装不了，要先跳到系统浏览器；</li>
 *   <li><b>manual</b>：其余（国产手机自带浏览器、Firefox…），能不能装看浏览器菜单里有没有那一项；</li>
 *   <li><b>installed</b>：已经是从桌面图标打开的，或刚刚装上。</li>
 * </ul>
 */

export type InstallMode = 'installed' | 'prompt' | 'ios' | 'in-app' | 'manual'

/** `beforeinstallprompt` 不在 TS 的 DOM 类型里（只有 Chromium 系实现了它） */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

/** App 内置浏览器的 UA 特征。QQ 浏览器（MQQBrowser）是独立浏览器，能装，不在此列 —— 所以 QQ 要带斜杠 */
const IN_APP_BROWSER = /MicroMessenger|\sQQ\/|Weibo|AlipayClient|DingTalk/i

/*
 * 模块级状态：`beforeinstallprompt` 在页面加载早期就发，可能早于任何组件挂载，
 * 所以由 main.ts 在启动时调 listenInstallEvents() 先接住，页面晚点来取。
 */
const deferredPrompt = shallowRef<BeforeInstallPromptEvent | null>(null)
const installed = ref(false)

function isStandalone(): boolean {
  const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true
  if (typeof window.matchMedia !== 'function') {
    return iosStandalone
  }
  return window.matchMedia('(display-mode: standalone)').matches || iosStandalone
}

function isIos(): boolean {
  const ua = navigator.userAgent
  // iPadOS 13 起 Safari 默认报桌面版 UA（Macintosh），只能靠触点数认出来
  return /iPhone|iPad|iPod/.test(ua) || (ua.includes('Macintosh') && navigator.maxTouchPoints > 1)
}

/** 启动时调一次。没有它的话，进到设置页之前就发出的安装事件会丢掉 */
export function listenInstallEvents(): void {
  installed.value = isStandalone()
  // 不 preventDefault：Chrome 自己的「安装」小横幅照常出，那是免费的曝光；我们只是把事件留一份给设置页用
  window.addEventListener('beforeinstallprompt', (event) => {
    deferredPrompt.value = event as BeforeInstallPromptEvent
  })
  window.addEventListener('appinstalled', () => {
    installed.value = true
    deferredPrompt.value = null
  })
}

export function useInstallApp(): {
  mode: ComputedRef<InstallMode>
  install: () => Promise<boolean>
} {
  const mode = computed<InstallMode>(() => {
    if (installed.value) {
      return 'installed'
    }
    if (deferredPrompt.value !== null) {
      return 'prompt'
    }
    if (IN_APP_BROWSER.test(navigator.userAgent)) {
      return 'in-app'
    }
    return isIos() ? 'ios' : 'manual'
  })

  /** 弹系统安装框。返回用户是否点了「安装」；当前不是 prompt 模式时直接返回 false */
  async function install(): Promise<boolean> {
    const event = deferredPrompt.value
    if (event === null) {
      return false
    }
    // 一个事件只能 prompt 一次，用过就作废；用户拒绝后浏览器过一阵会再发新的
    deferredPrompt.value = null
    await event.prompt()
    const { outcome } = await event.userChoice
    if (outcome === 'accepted') {
      installed.value = true
    }
    return outcome === 'accepted'
  }

  return { mode, install }
}

/** 仅供测试：回到「刚打开页面」的状态 */
export function resetInstallForTest(): void {
  deferredPrompt.value = null
  installed.value = false
}
