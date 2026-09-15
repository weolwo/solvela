package solvela.ledger.coupon.writeoff.domain;

import solvela.enums.CouponDeductTargetEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试算结果里的一张券。
 *
 * @param couponId       会员券 id。锁定时传的就是它
 * @param couponCode     券模编码
 * @param couponName     券名，直接显示给用户
 * @param discountAmount 这一单能减多少。<b>不可用时为 0</b>
 * @param usable         能不能用
 * @param reason         用不了的原因，可用时为 null。
 *                       🔴 这一项必须给出来 —— 用户手里有券却在下单页看不到它，
 *                       第一反应是系统坏了
 * @param validEndTime   失效时间。平局时先用快过期的那张
 * @param deductTarget   这张券减的是<b>哪一半</b>：{@code SCORE} 减积分、{@code CASH} 减现金。
 *                       🔴 混合支付单上调用方必须靠它决定把抵扣落在 payPoints 还是 payCash ——
 *                       落错一边就是「用户以为省了积分，实际少付了现金」
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record CouponTrialItem(Long couponId,
                              String couponCode,
                              String couponName,
                              BigDecimal discountAmount,
                              boolean usable,
                              CouponUnusableReason reason,
                              LocalDateTime validEndTime,
                              CouponDeductTargetEnum deductTarget) {

    public static CouponTrialItem usable(Long couponId, String couponCode, String couponName,
                                         BigDecimal discountAmount, LocalDateTime validEndTime,
                                         CouponDeductTargetEnum deductTarget) {
        return new CouponTrialItem(couponId, couponCode, couponName, discountAmount,
                true, null, validEndTime, deductTarget);
    }

    public static CouponTrialItem unusable(Long couponId, String couponCode, String couponName,
                                           CouponUnusableReason reason, LocalDateTime validEndTime,
                                           CouponDeductTargetEnum deductTarget) {
        return new CouponTrialItem(couponId, couponCode, couponName, BigDecimal.ZERO,
                false, reason, validEndTime, deductTarget);
    }
}
