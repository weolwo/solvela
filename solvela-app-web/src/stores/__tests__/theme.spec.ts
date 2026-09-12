import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { useThemeStore } from '../theme'

/**
 * 主题。
 *
 * <h3>🔴 这里最要紧的一条：打到 html 上的永远是 light 或 dark</h3>
 * 「跟随系统」在 store 里就被解析掉了，不会原样落到 DOM 上。
 * 这不是实现细节 —— theme.css 正是靠它才只写了<b>一段</b>深色值。
 * 哪天有人把 `system` 直接写上去，深色会静默失效：
 * 页面不报错，只是永远是浅色，而用户明明选了「跟随系统」。
 *
 * <h3>两个维度必须互不干扰</h3>
 * 切皮肤不该动深浅，切深浅不该动皮肤。混在一起的表现是
 * 「我只是想调暗一点，结果整个界面的圆角都变了」。
 */

/** 让 matchMedia 可控。jsdom 默认没有这个 API */
function mockMatchMedia(dark: boolean): { fire: (nowDark: boolean) => void } {
  const listeners: ((e: { matches: boolean }) => void)[] = []
  let matches = dark
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('dark') ? matches : false,
    media: query,
    addEventListener: (_: string, cb: (e: { matches: boolean }) => void) => listeners.push(cb),
    removeEventListener: () => {},
  }))
  return {
    fire: (nowDark: boolean) => {
      matches = nowDark
      listeners.forEach((cb) => cb({ matches: nowDark }))
    },
  }
}

function attrs() {
  const root = document.documentElement
  return {
    appearance: root.dataset['appearance'],
    skin: root.dataset['skin'],
    colorScheme: root.style.colorScheme,
  }
}

beforeEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('data-appearance')
  document.documentElement.removeAttribute('data-skin')
  document.documentElement.style.colorScheme = ''
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('跟随系统', () => {
  it('🔴 system 被解析成 dark 落到 html 上，而不是原样写 system', async () => {
    mockMatchMedia(true)
    const theme = useThemeStore()
    await nextTick()

    expect(theme.appearance, '用户选的仍然是「跟随系统」').toBe('system')
    expect(
      attrs().appearance,
      'CSS 只认 light / dark。写上 system 的话深色会静默失效 —— ' + '页面不报错，只是永远是浅色',
    ).toBe('dark')
    expect(theme.isDark).toBe(true)
  })

  it('系统是浅色时解析成 light', async () => {
    mockMatchMedia(false)
    useThemeStore()
    await nextTick()

    expect(attrs().appearance).toBe('light')
  })

  it('🔴 系统深浅变了要当场跟上，不用刷新', async () => {
    const media = mockMatchMedia(false)
    const theme = useThemeStore()
    await nextTick()
    expect(theme.isDark).toBe(false)

    media.fire(true)
    await nextTick()

    expect(theme.isDark, '不订阅的话要等下次刷新才变，用户会以为这个开关坏了').toBe(true)
    expect(attrs().appearance).toBe('dark')
  })

  it('手动选了浅色之后，系统变深色也不跟了 —— 那是他明确的选择', async () => {
    const media = mockMatchMedia(false)
    const theme = useThemeStore()
    theme.selectAppearance('light')
    await nextTick()

    media.fire(true)
    await nextTick()

    expect(attrs().appearance).toBe('light')
  })
})

describe('两个维度互不干扰', () => {
  it('切皮肤不动深浅', async () => {
    mockMatchMedia(true)
    const theme = useThemeStore()
    await nextTick()

    theme.selectSkin('ios')
    await nextTick()

    expect(attrs().skin).toBe('ios')
    expect(attrs().appearance, '只是想换个观感，结果连深浅都变了').toBe('dark')
  })

  it('切深浅不动皮肤', async () => {
    mockMatchMedia(false)
    const theme = useThemeStore()
    theme.selectSkin('ios')
    await nextTick()

    theme.selectAppearance('dark')
    await nextTick()

    expect(attrs().appearance).toBe('dark')
    expect(attrs().skin, '只是想调暗一点，结果整个界面的圆角都变了').toBe('ios')
  })
})

describe('持久化与兜底', () => {
  it('选过的下次还在', () => {
    mockMatchMedia(false)
    useThemeStore().selectAppearance('dark')
    useThemeStore().selectSkin('ios')

    setActivePinia(createPinia())
    const reopened = useThemeStore()

    expect(reopened.appearance).toBe('dark')
    expect(reopened.skin).toBe('ios')
  })

  it('🔴 存着的是认不出的值 → 回默认，而不是原样打上去', async () => {
    mockMatchMedia(false)
    localStorage.setItem('solvela.app.appearance', 'neon')
    localStorage.setItem('solvela.app.skin', 'windows95')

    const theme = useThemeStore()
    await nextTick()

    expect(theme.appearance, '原样打上去的话，页面会变成一套没有任何变量的裸样式').toBe('system')
    expect(theme.skin).toBe('default')
  })

  it('color-scheme 也要跟着切 —— 滚动条和原生控件不归我们的 CSS 管', async () => {
    mockMatchMedia(false)
    const theme = useThemeStore()
    theme.selectAppearance('dark')
    await nextTick()

    expect(attrs().colorScheme, '不设的话深色页面上会出现一条亮白的滚动条').toBe('dark')
  })
})

describe('「我的」页上显示的当前值', () => {
  it('默认皮肤时只说深浅 —— 多数人根本不知道还有皮肤这回事', () => {
    mockMatchMedia(false)
    const theme = useThemeStore()
    theme.selectAppearance('dark')

    expect(theme.label).toBe('深色')
  })

  it('换了皮肤才把两个都说出来', () => {
    mockMatchMedia(false)
    const theme = useThemeStore()
    theme.selectSkin('ios')
    theme.selectAppearance('dark')

    expect(theme.label).toBe('iOS 风格 · 深色')
  })
})
