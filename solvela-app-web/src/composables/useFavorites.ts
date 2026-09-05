import { ref } from 'vue'

import { ApiError } from '@/api/errors'
import { toggleFavorite } from '@/api/mall'
import type { Id } from '@/types/contract'

/**
 * 商品收藏的本地状态与乐观更新。**商品粒度，不是 SKU 粒度**。
 *
 * <h3>为什么需要一张「覆盖表」，而不是直接改商品对象</h3>
 * 🔴 {@link import('./useAsync').useAsync} 用的是 `shallowRef`，
 * 数组里对象的属性变化<b>根本不会触发重渲染</b> —— 直接写 `commodity.favorite = true`
 * 的话点了心形没反应，而且编译过、类型对、lint 过，只有真点一下才看得出来。
 * 所以真值是「后端给的 favorite，被这张表覆盖」。
 *
 * <p>首页和商城两个 pane 都要收藏，各写一份迟早会出现「一边乐观更新一边不」，
 * 所以收进这里。<b>两个 pane 各自持有一份实例</b>，不共享 ——
 * 跨 pane 的一致性要等真接口能持久化了再谈，现在假装共享只会让人以为它真的同步了。
 */
export interface Favorites {
  isFavorite: (commodityId: Id, serverValue: boolean) => boolean
  /** 正在提交的商品；非 null 时按钮该禁用，避免连点把状态点乱 */
  pending: ReturnType<typeof ref<Id | null>>
  /** 上一次失败的原因，空串表示没出错 */
  error: ReturnType<typeof ref<string>>
  toggle: (commodityId: Id, serverValue: boolean) => Promise<void>
}

export function useFavorites(): Favorites {
  const overrides = ref<Record<string, boolean>>({})
  const pending = ref<Id | null>(null)
  const error = ref('')

  function isFavorite(commodityId: Id, serverValue: boolean): boolean {
    return overrides.value[commodityId] ?? serverValue
  }

  /**
   * 乐观更新：先翻本地状态，失败再翻回来。
   *
   * 收藏是高频、低风险的动作 —— 等一个网络往返才变心形，手感上就是「点了没反应」。
   * 反过来，失败时必须<b>翻回去并说一句</b>，否则用户以为收藏成功了，下次进来发现没了。
   */
  async function toggle(commodityId: Id, serverValue: boolean): Promise<void> {
    if (pending.value !== null) {
      return
    }
    const next = !isFavorite(commodityId, serverValue)
    overrides.value = { ...overrides.value, [commodityId]: next }
    pending.value = commodityId
    error.value = ''
    try {
      await toggleFavorite(commodityId, next)
    } catch (e) {
      overrides.value = { ...overrides.value, [commodityId]: !next }
      error.value = e instanceof ApiError ? e.message : '操作失败，请稍后再试'
    } finally {
      pending.value = null
    }
  }

  return { isFavorite, pending, error, toggle }
}
