import { computed, type ComputedRef, type Ref, ref, shallowRef } from 'vue'

import type { Id } from '@/types/contract'

/**
 * 选券：试算 → 挑一张 → 条件一变就重算并清掉已选的。兑换页与充话费页共用。
 *
 * <h3>🔴 条件一变，已选的券就清掉</h3>
 * 换了规格、件数、面额之后，原来那张可能已经不满足门槛了。不清的话，用户会带着一张用不了的券去下单，
 * 然后被后端拒掉 —— 而页面上那张券还好端端地显示着「可减 10」。
 *
 * <h3>🔴 只认最后一次试算</h3>
 * 用户把面额从 100 改成 10，两次试算可能乱序回来。晚到的那份是旧条件的 ——
 * 认了它，页面上就会出现一张这一单根本用不了的券。
 *
 * <h3>试算挂了按「没有可用券」处理</h3>
 * 选券不该挡住下单本身：用户还是可以不用券把单下了。静默降级，而不是把整页变成错误态。
 *
 * <p>⚠️ 试算是<b>服务端</b>算的，金额一个都不在端上重算；下单时也只报「用哪张」，不报「减多少」。
 *
 * <pre>
 * const coupons = useCouponPicker({ load: () => trial(amount.value), usableOf: (r) => r.usable, empty })
 * watch(amount, (value) => coupons.reload(value !== null))
 * </pre>
 */
export interface CouponPicker<T extends { couponId: Id }, R> {
  /** 最近一次试算的原样结果；没试算、试算失败、条件不全时是 {@code empty} */
  result: Ref<R>
  loading: Ref<boolean>
  /** 能用的券（拍平）。只用来数数和按 couponId 找回选的那张 */
  usable: ComputedRef<T[]>
  /** 选中的券号。null = 不使用优惠券，这是默认 */
  chosenId: Ref<Id | null>
  chosen: ComputedRef<T | null>
  pickerOpen: Ref<boolean>
  /**
   * 条件变了：清掉已选的券和上一次的结果，然后重新试算。
   *
   * @param ready 条件齐了没有（规格还没选、面额还没挑时传 false：只清不算）
   */
  reload: (ready?: boolean) => Promise<void>
  /** 选一张（null = 不用券），并收起选择列表 */
  pick: (couponId: Id | null) => void
}

export function useCouponPicker<T extends { couponId: Id }, R>(options: {
  load: () => Promise<R>
  usableOf: (result: R) => T[]
  empty: R
}): CouponPicker<T, R> {
  const result = shallowRef<R>(options.empty) as Ref<R>
  const loading = ref(false)
  const chosenId = ref<Id | null>(null)
  const pickerOpen = ref(false)

  const usable = computed(() => options.usableOf(result.value))
  const chosen = computed(
    () => usable.value.find((item) => item.couponId === chosenId.value) ?? null,
  )

  /** 每次试算一个序号；回来时序号已经不是最新的，结果直接丢掉 */
  let latest = 0

  async function reload(ready = true): Promise<void> {
    const mine = ++latest
    chosenId.value = null
    pickerOpen.value = false
    result.value = options.empty
    loading.value = ready
    if (!ready) {
      return
    }
    try {
      const loaded = await options.load()
      if (mine === latest) {
        result.value = loaded
      }
    } catch {
      // 降级成「没有可用券」：result 已经是 empty 了
    } finally {
      if (mine === latest) {
        loading.value = false
      }
    }
  }

  function pick(couponId: Id | null): void {
    chosenId.value = couponId
    pickerOpen.value = false
  }

  return { result, loading, usable, chosenId, chosen, pickerOpen, reload, pick }
}
