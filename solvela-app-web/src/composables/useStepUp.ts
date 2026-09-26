import { readonly, ref, type Ref } from 'vue'

/**
 * 二次验证弹窗的状态：一个**全局单例**，由 http 拦截器唤起、由 StepUpDialog 结束。
 *
 * <h3>为什么拆成这一层，而不是 http.ts 直接弹框</h3>
 * api 层不认识 .vue（前端分层约定：api 不 import .vue），而弹框必须是组件。
 * 所以中间隔一个纯状态的模块：http 只拿到一个「返回 Promise&lt;boolean&gt; 的函数」
 * （由 stores/auth 注入），组件只读 {@link useStepUpState} 并调 {@link finishStepUp}。
 * 两边谁也不知道谁。
 *
 * <h3>一次只有一个在途</h3>
 * http.ts 已经把并发的 STEP_UP_REQUIRED 合并成一次（见 handleStepUp），
 * 所以正常情况下这里不会同时有两个等待者。万一有，旧的那个按「放弃」结束，
 * 而不是悬在那里永远不 resolve —— 悬着的表现是某个按钮永远在转圈。
 */

const open = ref(false)

let resolver: ((verified: boolean) => void) | null = null

/** 唤起弹窗，等用户验证完。true = 通过，false = 放弃 */
export function requestStepUp(): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    resolver?.(false)
    resolver = resolve
    open.value = true
  })
}

/** 结束本次验证。弹窗在「验证通过」「用户取消」「退出登录」时调 */
export function finishStepUp(verified: boolean): void {
  open.value = false
  const pending = resolver
  resolver = null
  pending?.(verified)
}

export function useStepUpState(): { open: Readonly<Ref<boolean>> } {
  return { open: readonly(open) }
}
