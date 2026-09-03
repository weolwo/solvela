import { defineStore } from 'pinia'
import { computed, ref, watchEffect } from 'vue'

/**
 * 主题（换肤）。
 *
 * <h3>🔴 目前只有一套皮肤，这里是"接口先立住"</h3>
 * theme.css 顶上写着「活动换皮肤时要能只改这一层」—— 这个 store 就是那个开关将来的家。
 * 换肤的做法是在 `<html>` 上打一个 `data-theme`，由 CSS 覆盖 `--sv-*` 变量，
 * 组件一行都不用改。
 *
 * <p>现在只有 `default` 一个值，所以「我的」页面里那个入口是**只读展示**，
 * 不是一个点了没反应的按钮。等第二套皮肤落地再放开选择。
 *
 * <p>⚠️ 注意这不是「深色模式」。深色模式要为每个 `--sv-*` 再定义一套值
 * 并处理 `prefers-color-scheme`，是独立的一件事；换肤是活动运营的诉求。
 * 两者共用这套机制，但不要混为一谈。
 */

export const THEMES = [{ id: 'default', label: '默认' }] as const

export type ThemeId = (typeof THEMES)[number]['id']

const STORAGE_KEY = 'solvela.app.theme'

function read(): ThemeId {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    // 存的值可能是被删掉的旧皮肤，认不出就回默认，不能让页面变成没有变量的裸样式
    return THEMES.some((t) => t.id === saved) ? (saved as ThemeId) : 'default'
  } catch {
    // 隐私模式下 localStorage 会直接抛
    return 'default'
  }
}

export const useThemeStore = defineStore('theme', () => {
  const current = ref<ThemeId>(read())

  const label = computed(() => THEMES.find((t) => t.id === current.value)?.label ?? '默认')

  /** 主题落到 <html> 的 data-theme 上，CSS 据它覆盖 --sv-* */
  watchEffect(() => {
    document.documentElement.dataset['theme'] = current.value
    try {
      localStorage.setItem(STORAGE_KEY, current.value)
    } catch {
      // 存不下就只在本次会话生效，不影响使用
    }
  })

  function select(id: ThemeId): void {
    current.value = id
  }

  return { current, label, select }
})
