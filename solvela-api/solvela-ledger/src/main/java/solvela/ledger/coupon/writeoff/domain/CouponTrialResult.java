package solvela.ledger.coupon.writeoff.domain;

import solvela.enums.CouponDeductTargetEnum;

import java.util.List;

/**
 * 试算结果：<b>按抵扣对象分组</b>，每组内推荐一张。
 *
 * <h3>🔴 为什么分组，而不是给一个「全局最优」</h3>
 * 一张减 10 积分的券和一张减 5 元的券，<b>谁更划算系统答不了</b> ——
 * 1 积分 ≠ 1 元，而汇率是业务定义、还会变。硬给一个全局最优，
 * 就是替用户做了一个它没有依据的决定。所以组内排序，<b>跨组让用户自己挑</b>。
 *
 * <h3>⚠️ 2026-09-15 改回了这个形状</h3>
 * 阶段 4 曾经简化成「入参指定一个抵扣对象、返回一个扁平列表」，
 * 理由是「一笔订单的应付本来就是确定的」。那对纯积分单成立，
 * 对<b>混合支付单</b>（积分 + 现金）不成立 —— 当时为此定了条
 * 「混合单只抵现金」的规则，把 {@code SCORE} 券挡在了门外，
 * 而那条规则是把叠加场景的顾虑套错了地方。
 *
 * <p>不可比的是<b>推荐</b>，不是<b>可用性</b>。现在两种券都参与试算，
 * 只是不跨组比大小。
 *
 * @param groups   按抵扣对象分的组。<b>这一单没有的那一侧不会出现</b>
 *                 （纯积分单就只有 SCORE 一组）
 * @param unusable 用不了的券，每张带着原因。<b>要展示，别过滤</b> ——
 *                 用户手里有券却看不到它，第一反应是系统坏了
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record CouponTrialResult(List<Group> groups, List<CouponTrialItem> unusable) {

    /**
     * 一个抵扣对象下的可用券。
     *
     * @param items       组内的券，<b>按抵扣额从大到小</b>，平局时快过期的在前
     * @param recommended 组内最优（= {@code items} 的第一张）
     */
    public record Group(CouponDeductTargetEnum deductTarget,
                        List<CouponTrialItem> items,
                        CouponTrialItem recommended) {

        public static Group of(CouponDeductTargetEnum deductTarget, List<CouponTrialItem> items) {
            return new Group(deductTarget, List.copyOf(items), items.get(0));
        }
    }

    /** 全部可用券拍平。调用方要按 couponId 找回用户选的那张时用得上 */
    public List<CouponTrialItem> allUsable() {
        return groups.stream().flatMap(g -> g.items().stream()).toList();
    }
}
