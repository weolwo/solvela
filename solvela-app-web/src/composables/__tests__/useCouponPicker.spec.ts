import { describe, expect, it } from 'vitest'

import { useCouponPicker } from '@/composables/useCouponPicker'
import { type Id, toId } from '@/types/contract'

interface Item {
  couponId: Id
  name: string
}

interface Trial {
  usable: Item[]
}

const EMPTY: Trial = { usable: [] }

function item(id: string): Item {
  return { couponId: toId(id), name: `券${id}` }
}

/** 一个可以从外面决定什么时候、以什么结果回来的试算 */
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

describe('useCouponPicker', () => {
  it('试算回来之后能挑一张；挑完收起列表', async () => {
    const picker = useCouponPicker({
      load: () => Promise.resolve({ usable: [item('1'), item('2')] }),
      usableOf: (r: Trial) => r.usable,
      empty: EMPTY,
    })

    await picker.reload()
    picker.pickerOpen.value = true
    picker.pick(toId('2'))

    expect(picker.usable.value).toHaveLength(2)
    expect(picker.chosen.value?.name).toBe('券2')
    expect(picker.pickerOpen.value).toBe(false)
  })

  it('🔴 条件一变就清掉已选的券 —— 原来那张可能已经不满足门槛了', async () => {
    const picker = useCouponPicker({
      load: () => Promise.resolve({ usable: [item('1')] }),
      usableOf: (r: Trial) => r.usable,
      empty: EMPTY,
    })
    await picker.reload()
    picker.pick(toId('1'))

    await picker.reload()

    expect(picker.chosenId.value).toBeNull()
    expect(picker.chosen.value).toBeNull()
  })

  it('🔴 两次试算乱序回来，只认最后一次 —— 晚到的那份是旧条件的', async () => {
    const first = deferred<Trial>()
    const second = deferred<Trial>()
    const pending = [first, second]
    const picker = useCouponPicker({
      load: () => pending.shift()!.promise,
      usableOf: (r: Trial) => r.usable,
      empty: EMPTY,
    })

    const a = picker.reload()
    const b = picker.reload()
    second.resolve({ usable: [item('new')] })
    await b
    first.resolve({ usable: [item('old')] })
    await a

    expect(picker.usable.value.map((c) => c.name)).toEqual(['券new'])
    expect(picker.loading.value).toBe(false)
  })

  it('试算挂了按「没有可用券」处理，不往外抛 —— 选券不该挡住下单本身', async () => {
    const picker = useCouponPicker({
      load: () => Promise.reject(new Error('网络错误')),
      usableOf: (r: Trial) => r.usable,
      empty: EMPTY,
    })

    await expect(picker.reload()).resolves.toBeUndefined()
    expect(picker.usable.value).toEqual([])
    expect(picker.loading.value).toBe(false)
  })

  it('条件不全（还没选规格 / 面额）时只清不算', async () => {
    let calls = 0
    const picker = useCouponPicker({
      load: () => {
        calls++
        return Promise.resolve({ usable: [item('1')] })
      },
      usableOf: (r: Trial) => r.usable,
      empty: EMPTY,
    })

    await picker.reload(false)

    expect(calls).toBe(0)
    expect(picker.loading.value).toBe(false)
  })
})
