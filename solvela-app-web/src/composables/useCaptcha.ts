import { readonly, ref, type Ref } from 'vue'

/**
 * 滑块验证码弹窗的状态：全局单例，由 http 拦截器唤起、由 CaptchaDialog 结束。
 * 结构与 useStepUp 相同（api 层不认识 .vue，中间隔一层纯状态），见那边的注释。
 *
 * 唤起者拿到的是通行票（拖对了）或 null（用户放弃）。
 */

const open = ref(false)

let resolver: ((token: string | null) => void) | null = null

/** 唤起滑块，等用户拖完。返回通行票；放弃返回 null */
export function requestCaptcha(): Promise<string | null> {
  return new Promise<string | null>((resolve) => {
    resolver?.(null)
    resolver = resolve
    open.value = true
  })
}

/** 结束本次验证。拖对了传通行票，取消传 null */
export function finishCaptcha(token: string | null): void {
  open.value = false
  const pending = resolver
  resolver = null
  pending?.(token)
}

export function useCaptchaState(): { open: Readonly<Ref<boolean>> } {
  return { open: readonly(open) }
}
