package solvela.member.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试算结果：<b>按抵扣对象分组</b>，每组内推荐一张。
 *
 * <h3>🔴 为什么不给一个「全局最优」</h3>
 * 一张减 10 积分的券和一张减 5 元的券，<b>谁更划算系统答不了</b> ——
 * 1 积分 ≠ 1 元，汇率是业务定义、还会变。硬给一个全局最优，
 * 就是替用户做了一个它没有依据的决定。组内排序，<b>跨组让用户自己挑</b>。
 *
 * @param groups   按抵扣对象分的组。这一单没有的那一侧不会出现
 *                 （纯积分单就只有 SCORE 一组）
 * @param unusable 用不了的券，每张带着原因。<b>要展示出来，别过滤掉</b> ——
 *                 用户手里有券却看不到它，第一反应是系统坏了
 */
public record CouponTrialView(List<Group> groups, List<Item> unusable) {

    /**
     * @param deductTarget {@code CASH} / {@code SCORE}
     * @param items        组内的券，已按能减多少排好，第一张就是本组最优
     */
    public record Group(String deductTarget, List<Item> items) {
    }

    /**
     * 试算结果里的一张券。
     *
     * @param discountAmount 这一单能减多少。不可用时为 0
     * @param reason         不可用的原因码（对齐 {@code CouponUnusableReason}），可用时为 null
     * @param reasonDesc     原因的人话版本，直接显示。
     *                       🔴 <b>必须给出来</b> —— 「没到门槛」这一句能省掉一次客服
     * @param deductTarget   这张券减的是哪一半。
     *                       🔴 混合单上调用方靠它决定把抵扣落在积分还是现金侧
     */
    public record Item(Long couponId,
                       String couponName,
                       BigDecimal discountAmount,
                       boolean usable,
                       String reason,
                       String reasonDesc,
                       LocalDateTime validEndTime,
                       String deductTarget) {
    }

    /** 全部可用券拍平。调用方按 couponId 找回用户选的那张时用得上 */
    public List<Item> allUsable() {
        return groups.stream().flatMap(g -> g.items().stream()).toList();
    }
}
