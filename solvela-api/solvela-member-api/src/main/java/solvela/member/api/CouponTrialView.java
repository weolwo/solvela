package solvela.member.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试算结果。
 *
 * <h3>🔴 可用与不可用刻意分成两个列表</h3>
 * 混在一起的话，调用方最省事的写法就是把整个列表铺出来，然后用户看到一堆点不动的券；
 * 而真要区分，又得每个调用方自己写一遍过滤。
 *
 * @param usable      能用的券，<b>按抵扣额从大到小</b>，平局时快过期的在前
 * @param unusable    用不了的券，每张带着原因。<b>要展示出来，别过滤掉</b>
 * @param recommended 最优券（= {@code usable} 的第一张）；一张能用的都没有时为 null
 */
public record CouponTrialView(List<Item> usable, List<Item> unusable, Item recommended) {

    /**
     * 试算结果里的一张券。
     *
     * @param couponId       会员券 id。锁定时传的就是它
     * @param couponName     券名，直接显示给用户
     * @param discountAmount 这一单能减多少。不可用时为 0
     * @param usable         能不能用
     * @param reason         用不了的原因码（对齐 {@code CouponUnusableReason}），可用时为 null
     * @param reasonDesc     原因的人话版本，直接显示给用户。
     *                       🔴 <b>必须给出来</b> —— 「没到门槛」这一句话能省掉一次客服
     * @param validEndTime   失效时间
     */
    public record Item(Long couponId,
                       String couponName,
                       BigDecimal discountAmount,
                       boolean usable,
                       String reason,
                       String reasonDesc,
                       LocalDateTime validEndTime) {
    }
}
