import { defineStore } from 'pinia'
import { computed, ref, watchEffect } from 'vue'

/**
 * 主题。**两个互相独立的维度**，不要混成一个。
 *
 * <table>
 *   <tr><td></td><td>外观 appearance</td><td>皮肤 skin</td></tr>
 *   <tr><td>管什么</td><td>深浅（一套颜色）</td><td>平台观感（圆角/间距/尺寸）</td></tr>
 *   <tr><td>谁在用</td><td>用户，随时会切</td><td>基本不切，选一次</td></tr>
 *   <tr><td>取值</td><td>system / light / dark</td><td>default / ios</td></tr>
 * </table>
 *
 * 合成一个枚举（「浅色」「深色」「iOS 浅色」「iOS 深色」）的话，
 * 加第三套皮肤要写六个值，而且「我现在是不是深色」这个问题要靠字符串匹配来答。
 *
 * <h3>🔴 「跟随系统」在这里解析掉，不留给 CSS</h3>
 * 打到 {@code <html>} 上的 {@code data-appearance} <b>永远是 light 或 dark</b>，
 * 不会是 system。这样 theme.css 里只需要一段 {@code [data-appearance='dark']}；
 * 交给 CSS 的话，同一套深色值要在媒体查询里再抄一份，
 * 而改漏一份的表现是「手动选深色是对的，跟随系统时有几个颜色不对」。
 *
 * <p>代价是要自己监听系统变化 —— 见下面的 matchMedia 订阅。
 */

export const APPEARANCES = [
  { id: 'system', label: '跟随系统' },
  { id: 'light', label: '浅色' },
  { id: 'dark', label: '深色' },
] as const

export type AppearanceId = (typeof APPEARANCES)[number]['id']

export const SKINS = [
  { id: 'default', label: '默认', hint: '鸿蒙风格：大圆角、胶囊按钮' },
  { id: 'ios', label: 'iOS 风格', hint: '小圆角、更紧的留白' },
] as const

export type SkinId = (typeof SKINS)[number]['id']

const APPEARANCE_KEY = 'solvela.app.appearance'
const SKIN_KEY = 'solvela.app.skin'

/**
 * 隐私模式 / 禁用站点数据时，访问 storage 会直接抛（不是返回 null）。
 * 与 token-storage 同一个处理：不能让它掀翻整个应用。
 */
function safeRead(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeWrite(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 存不下就只在本次会话生效，不影响使用
  }
}

/** 存的值可能是被删掉的旧主题，认不出就回默认 —— 不能让页面变成没有变量的裸样式 */
function readAppearance(): AppearanceId {
  const saved = safeRead(APPEARANCE_KEY)
  return APPEARANCES.some((a) => a.id === saved) ? (saved as AppearanceId) : 'system'
}

function readSkin(): SkinId {
  const saved = safeRead(SKIN_KEY)
  return SKINS.some((s) => s.id === saved) ? (saved as SkinId) : 'default'
}

const DARK_QUERY = '(prefers-color-scheme: dark)'

function systemPrefersDark(): boolean {
  try {
    return window.matchMedia(DARK_QUERY).matches
  } catch {
    // 老浏览器 / 测试环境里可能没有 matchMedia。当成浅色 —— 那是更安全的默认
    return false
  }
}

export const useThemeStore = defineStore('theme', () => {
  const appearance = ref<AppearanceId>(readAppearance())
  const skin = ref<SkinId>(readSkin())
  /** 系统当前是不是深色。只有 appearance='system' 时用得上 */
  const systemDark = ref(systemPrefersDark())

  /**
   * 系统深浅变了要跟着变。
   *
   * 🔴 不订阅的话，用户在「跟随系统」下切换手机的深色模式，
   * 页面要等到下次刷新才跟上 —— 而他多半会以为这个开关坏了。
   */
  try {
    window.matchMedia(DARK_QUERY).addEventListener('change', (e) => {
      systemDark.value = e.matches
    })
  } catch {
    // 没有 matchMedia 就没有系统偏好可跟随，保持浅色
  }

  /** 最终生效的深浅。**只会是 light 或 dark** */
  const resolvedAppearance = computed<'light' | 'dark'>(() => {
    if (appearance.value === 'system') {
      return systemDark.value ? 'dark' : 'light'
    }
    return appearance.value
  })

  const isDark = computed(() => resolvedAppearance.value === 'dark')

  const appearanceLabel = computed(
    () => APPEARANCES.find((a) => a.id === appearance.value)?.label ?? '跟随系统',
  )
  const skinLabel = computed(() => SKINS.find((s) => s.id === skin.value)?.label ?? '默认')

  /** 「我的」页那一行右侧显示的当前值。两个维度都要，只显示一个说不清 */
  const label = computed(() =>
    skin.value === 'default'
      ? appearanceLabel.value
      : `${skinLabel.value} · ${appearanceLabel.value}`,
  )

  watchEffect(() => {
    const root = document.documentElement
    root.dataset['appearance'] = resolvedAppearance.value
    root.dataset['skin'] = skin.value
    /*
     * 🔴 同步告诉浏览器，让它把滚动条、表单控件、默认背景也切过去。
     * 不设的话，深色页面上会出现一条亮白的滚动条和白色的原生下拉框 ——
     * 那不是我们的 CSS 管得到的部分。
     */
    root.style.colorScheme = resolvedAppearance.value
  })

  function selectAppearance(id: AppearanceId): void {
    appearance.value = id
    safeWrite(APPEARANCE_KEY, id)
  }

  function selectSkin(id: SkinId): void {
    skin.value = id
    safeWrite(SKIN_KEY, id)
  }

  return {
    appearance,
    skin,
    resolvedAppearance,
    isDark,
    appearanceLabel,
    skinLabel,
    label,
    selectAppearance,
    selectSkin,
  }
})
