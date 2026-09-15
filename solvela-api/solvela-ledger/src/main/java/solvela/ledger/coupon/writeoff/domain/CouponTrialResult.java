package solvela.ledger.coupon.writeoff.domain;

import java.util.List;

/**
 * 试算结果。
 *
 * <h3>🔴 可用与不可用刻意分成两个列表</h3>
 * 混在一起的话，调用方最省事的写法就是把整个列表铺出来，然后用户看到一堆点不动的券；
 * 而真要区分，又得每个调用方自己写一遍过滤。分开之后「哪些能用」和
 * 「哪些为什么不能用」都是现成的，想写错都难。
 *
 * <h3>⚠️ 这里<b>只有一个</b> {@code deductTarget}，和方案 §4.1 的写法不同</h3>
 * §4.1 说试算输出要按 {@code deduct_target} <b>分组</b>、跨组让用户自己挑。
 * 落地时改成了<b>入参就要求指定抵扣对象</b>，理由是：一笔订单的应付本来就是确定的，
 * 纯积分单只可能抵积分。返回两组，等于把「1 积分值多少钱」这个问题推给调用方，
 * 而调用方同样答不了 —— 那正是 §4.1 想避免的事，只是换了个地方发生。
 *
 * <p>抵扣对象对不上的券不会被藏起来：它们出现在 {@link #unusable} 里，
 * 带着 {@link CouponUnusableReason#DEDUCT_TARGET_MISMATCH}。
 *
 * @param usable      能用的券，<b>按抵扣额从大到小</b>，平局时快过期的在前
 * @param unusable    用不了的券，每张带着原因
 * @param recommended 最优券（= {@code usable} 的第一张）；一张能用的都没有时为 null
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record CouponTrialResult(List<CouponTrialItem> usable,
                                List<CouponTrialItem> unusable,
                                CouponTrialItem recommended) {

    public static CouponTrialResult of(List<CouponTrialItem> usable, List<CouponTrialItem> unusable) {
        return new CouponTrialResult(List.copyOf(usable), List.copyOf(unusable),
                usable.isEmpty() ? null : usable.get(0));
    }
}
