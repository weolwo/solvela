import { ref, shallowRef, type Ref, type ShallowRef } from 'vue'

import { ApiError } from '@/api/errors'

/**
 * 一次异步取数的四个状态：加载中 / 有数据 / 空 / 出错。
 *
 * <h3>为什么要有这个</h3>
 * 三个 Tab 页都是「进页面取一次数」，每个页面各写一遍
 * `loading/data/error` 三个 ref 加一个 try-catch，写到第三遍就会开始不一致 ——
 * 典型表现是某个页面忘了处理错误，请求失败时永远停在骨架屏上，
 * 而这种 bug 只在后端挂掉时才出现。
 *
 * <p>不做缓存、不做重试、不做并发去重。需要那些的时候再上库
 * （TanStack Query 之类），现在上是为还没有的问题付钱。
 */
export interface AsyncState<T> {
  data: ShallowRef<T | null>
  loading: Ref<boolean>
  /** 给用户看的一句话。null 表示没出错 */
  error: Ref<string | null>
  reload: () => Promise<void>
}

export function useAsync<T>(loader: () => Promise<T>): AsyncState<T> {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function reload(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      data.value = await loader()
    } catch (e) {
      // 后端给的 message 是给用户看的人话，优先用它；
      // 网络层的原始错误（Network Error 之类）对用户没有意义，换成一句人话
      error.value = e instanceof ApiError ? e.message : '加载失败，请下拉重试'
    } finally {
      loading.value = false
    }
  }

  void reload()

  return { data, loading, error, reload }
}
