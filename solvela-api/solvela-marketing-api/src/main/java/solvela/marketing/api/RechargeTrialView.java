package solvela.marketing.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充值选券的试算结果。
 *
 * <p>🔴 不可用的券<b>也要返回</b>，带着原因 —— 用户手里有券却在这一页看不到它，
 * 第一反应是系统坏了，而真实原因往往只是「没到门槛」。
 *
 * @param usable   能用的券，按抵扣额从大到小
 * @param unusable 用不了的券，每张带着一句人话原因
 */
public record RechargeTrialView(List<Item> usable, List<Item> unusable) {

    /**
     * @param discountAmount 这一笔能减多少，不可用时为 0
     * @param reasonDesc     用不了的原因（人话），可用时为 null
     */
    public record Item(Long couponId,
                       String couponName,
                       BigDecimal discountAmount,
                       boolean usable,
                       String reasonDesc) {
    }
}
