import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { useAsync } from '@/composables/useAsync'

/** 一个可以从外面决定什么时候、以什么结果回来的请求 */
function deferred<T>(): {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (error: unknown) => void
} {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useAsync', () => {
  it('构造即取一次；成功后 loading 归位', async () => {
    const state = useAsync(() => Promise.resolve('数据'))
    expect(state.loading.value).toBe(true)

    await state.reload()

    expect(state.data.value).toBe('数据')
    expect(state.loading.value).toBe(false)
    expect(state.error.value).toBeNull()
  })

  it('🔴 两次 reload 乱序回来，只认最后一次 —— 晚到的那份是旧条件的', async () => {
    const calls = [deferred<string>(), deferred<string>(), deferred<string>()]
    let n = 0
    const state = useAsync(() => calls[n++]!.promise)

    const first = state.reload()
    const second = state.reload()
    calls[2]!.resolve('已使用')
    await second
    calls[1]!.resolve('可用（旧条件）')
    calls[0]!.resolve('构造时那一次')
    await first

    expect(state.data.value).toBe('已使用')
    expect(state.loading.value).toBe(false)
  })

  it('旧请求晚到的错误也不覆盖新结果', async () => {
    const calls = [deferred<string>(), deferred<string>()]
    let n = 0
    const state = useAsync(() => calls[n++]!.promise)

    const latest = state.reload()
    calls[1]!.resolve('新')
    await latest
    calls[0]!.reject(new ApiError('INTERNAL', '服务开小差了', null, 500))
    await Promise.resolve()

    expect(state.data.value).toBe('新')
    expect(state.error.value).toBeNull()
  })

  it('出错时给一句人话：后端的 message 优先，网络层的原始错误换成固定文案', async () => {
    const fromServer = useAsync(() =>
      Promise.reject(new ApiError('CONFLICT', '活动暂未开放', null, 409)),
    )
    await fromServer.reload()
    expect(fromServer.error.value).toBe('活动暂未开放')

    const network = useAsync(() => Promise.reject(new Error('Network Error')))
    await network.reload()
    expect(network.error.value).toBe('加载失败，请下拉重试')
  })
})
