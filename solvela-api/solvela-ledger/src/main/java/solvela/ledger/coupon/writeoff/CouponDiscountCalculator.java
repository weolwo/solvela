package solvela.ledger.coupon.writeoff;

import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 「这张券在这一单上能减多少」。
 *
 * <p>单独成一个类，是因为方案 §4.1 里那五个细节<b>每一个漏掉都会出事</b>，
 * 而它们全部集中在这几十行里 —— 混在编排代码中间会被读漏。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
final class CouponDiscountCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CouponDiscountCalculator() {
    }

    /**
     * 算抵扣额。调用方保证门槛、范围、抵扣对象都已经判过了。
     *
     * <h3>🔴 ① 抵扣额不能超过应付</h3>
     * 一张「减 200」的券碰上 150 元的订单，算出来是 200 —— 应付变成 <b>-50</b>。
     * 这一条写死在这里，<b>不能指望调用方判</b>：漏判的表现不是报错，
     * 是订单金额变成负数然后一路往下走。
     *
     * <h3>🔴 ② 往下取整，永远不往上</h3>
     * 百分比券会算出小数。抵扣<b>只能向下取整</b>：向上是把平台的钱送出去，
     * 一单一分钱看着无所谓，但它是一条不受控的口子，而且没人会发现。
     *
     * <p>精度按抵扣对象定：抵现金保留 2 位，<b>抵积分取整</b> ——
     * 积分是整数，减 0.5 分这件事在钱包那边根本表达不了。
     */
    static BigDecimal compute(CouponDiscountTypeEnum discountType,
                              BigDecimal discountValue,
                              BigDecimal maxDiscount,
                              CouponDeductTargetEnum deductTarget,
                              BigDecimal payAmount) {
        BigDecimal raw = switch (discountType) {
            case FIXED -> discountValue;
            // discountValue 是折扣率（20 表示减 20%）
            case PERCENT -> payAmount.multiply(discountValue).divide(HUNDRED, 4, RoundingMode.DOWN);
        };

        /*
         * 封顶只对百分比券有意义。固定额券填了 max_discount 也按它压一次 ——
         * 那是运营配出来的自相矛盾（「减 20，但最多减 10」），
         * 取小的那个是唯一不会多付钱的解释。
         */
        if (maxDiscount != null && raw.compareTo(maxDiscount) > 0) {
            raw = maxDiscount;
        }

        // ① 不能超过应付
        if (raw.compareTo(payAmount) > 0) {
            raw = payAmount;
        }

        // ② 向下取整。积分是整数，现金两位小数
        int scale = deductTarget == CouponDeductTargetEnum.SCORE ? 0 : 2;
        BigDecimal result = raw.setScale(scale, RoundingMode.DOWN);

        return result.signum() < 0 ? BigDecimal.ZERO : result;
    }
}
