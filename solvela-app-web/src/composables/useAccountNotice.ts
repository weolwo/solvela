import { readonly, ref, type Ref } from 'vue'

import type { AssetOutUnlock } from '@/api/errors'

/**
 * 「账号被保护起来了」这类<b>只需要告知 + 指一条路</b>的全局弹窗状态。
 *
 * <h3>它和 useStepUp 的区别：这里没有「验完重试」</h3>
 * 二次验证是「还差一步，补上就继续」；这里是「这件事现在办不了，去办另一件」。
 * 所以唤起方不等任何结果（返回 void），原请求照常失败 ——
 * 假装成「可以重试」的话，用户会一直点那个按钮。
 *
 * <h3>为什么要拆这一层</h3>
 * 同 {@link useStepUp}：api 层不认识 .vue（前端分层约定：api 不 import .vue），
 * 而弹窗必须是组件。中间隔一个纯状态的模块，两边谁也不知道谁。
 */

export interface AccountNotice {
  /** 后端的错误码，决定弹窗的标题与按钮 */
  code: 'ASSET_OUT_FROZEN'
  /** 后端给的人话，原样展示 */
  message: string
  /** 解除方式。null 表示前端不认识服务端给的那种解法，此时只显示「知道了」 */
  unlock: AssetOutUnlock | null
}

const current = ref<AccountNotice | null>(null)

/**
 * 弹一条。
 *
 * <p>🔴 已经有一条挂着时<b>不覆盖</b>：并发的几个请求（比如保存地址后顺手刷列表）
 * 会各撞一次同一个码，覆盖等于让弹窗在用户眼前闪一下又换一个。
 */
export function showAccountNotice(notice: AccountNotice): void {
  if (current.value !== null) {
    return
  }
  current.value = notice
}

export function dismissAccountNotice(): void {
  current.value = null
}

export function useAccountNoticeState(): { current: Readonly<Ref<AccountNotice | null>> } {
  return { current: readonly(current) }
}
