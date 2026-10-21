import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

import { listenInstallEvents, resetInstallForTest, useInstallApp } from '../useInstallApp'

/**
 * 「添加到桌面」该给用户哪一种引导。判错的代价：
 * 在微信里教用户点「分享 → 添加到主屏幕」，用户照着点了也装不上；
 * 能一键安装的 Chrome 却只给一段说明，白白多了几步。
 */

const UA = {
  iphoneSafari:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
  iphoneWechat:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.50(0x18003234) NetType/WIFI Language/zh_CN',
  androidChrome:
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36',
  qqBrowser:
    'Mozilla/5.0 (Linux; Android 14; V2309A) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/109.0.5414.86 MQQBrowser/15.0 Mobile Safari/537.36',
}

function useUserAgent(ua: string): void {
  vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(ua)
}

/** 模拟 Chromium 发出的 beforeinstallprompt */
function firePrompt(outcome: 'accepted' | 'dismissed'): { prompt: ReturnType<typeof vi.fn> } {
  const prompt = vi.fn(() => Promise.resolve())
  const event = Object.assign(new Event('beforeinstallprompt'), {
    prompt,
    userChoice: Promise.resolve({ outcome }),
  })
  window.dispatchEvent(event)
  return { prompt }
}

beforeAll(() => {
  // 和 main.ts 一样只接一次
  listenInstallEvents()
})

beforeEach(() => {
  resetInstallForTest()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('判断怎么装', () => {
  it('iPhone Safari → 教用户点分享', () => {
    useUserAgent(UA.iphoneSafari)

    expect(useInstallApp().mode.value).toBe('ios')
  })

  it('🔴 微信里（哪怕是 iPhone）→ 先跳浏览器，不教「分享 → 添加到主屏幕」—— 微信里没有这一项', () => {
    useUserAgent(UA.iphoneWechat)

    expect(useInstallApp().mode.value).toBe('in-app')
  })

  it('QQ 浏览器是独立浏览器，不当成 App 内置浏览器', () => {
    useUserAgent(UA.qqBrowser)

    expect(useInstallApp().mode.value).toBe('manual')
  })

  it('Chrome 还没发安装事件 → 只能给说明', () => {
    useUserAgent(UA.androidChrome)

    expect(useInstallApp().mode.value).toBe('manual')
  })
})

describe('一键安装', () => {
  it('收到 beforeinstallprompt → 能弹系统安装框；用户点了安装 → 已添加', async () => {
    useUserAgent(UA.androidChrome)
    const { mode, install } = useInstallApp()
    const { prompt } = firePrompt('accepted')
    expect(mode.value).toBe('prompt')

    expect(await install()).toBe(true)

    expect(prompt).toHaveBeenCalledTimes(1)
    expect(mode.value).toBe('installed')
  })

  it('用户拒绝 → 事件作废（一个事件只能弹一次），退回说明', async () => {
    useUserAgent(UA.androidChrome)
    const { mode, install } = useInstallApp()
    firePrompt('dismissed')

    expect(await install()).toBe(false)

    expect(mode.value).toBe('manual')
  })

  it('从浏览器菜单装上了（appinstalled）→ 设置页同步显示已添加', () => {
    useUserAgent(UA.androidChrome)
    const { mode } = useInstallApp()

    window.dispatchEvent(new Event('appinstalled'))

    expect(mode.value).toBe('installed')
  })
})
