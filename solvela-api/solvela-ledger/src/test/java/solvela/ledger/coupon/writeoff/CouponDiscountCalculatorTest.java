package solvela.ledger.coupon.writeoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「这张券能减多少」。
 *
 * <p>方案 §4.1 列了五个细节，<b>每一个漏掉都会出事</b>。这个类逐条守着它们。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
class CouponDiscountCalculatorTest {

    @Test
    @DisplayName("🔴 ① 抵扣不能超过应付：减 200 的券碰上 150 的订单，只能减 150")
    void 抵扣封顶在应付金额() {
        // 不拦的话应付变成 -50，而且不报错，一路往下走
        assertEquals(new BigDecimal("150.00"), fixed("200", null, "150"));
    }

    @Test
    @DisplayName("固定额券：减 20 就是减 20")
    void 固定额() {
        assertEquals(new BigDecimal("20.00"), fixed("20", null, "150"));
    }

    @Test
    @DisplayName("🔴 ② 百分比和固定额谁大不一定，必须逐张算")
    void 百分比与固定额没有捷径() {
        // 150 元：满100减20 → 20；8折最高减50 → min(30,50) = 30，百分比赢
        assertEquals(new BigDecimal("20.00"), fixed("20", null, "150"));
        assertEquals(new BigDecimal("30.00"), percent("20", "50", "150"));

        // 500 元：满100减20 → 20；8折最高减50 → min(100,50) = 50，还是百分比赢
        assertEquals(new BigDecimal("50.00"), percent("20", "50", "500"));

        // 但换个封顶就反过来了：8折最高减 10 → 10，固定额 20 赢
        assertEquals(new BigDecimal("10.00"), percent("20", "10", "500"));
    }

    @Test
    @DisplayName("🔴 百分比必须被 max_discount 压住 —— 不封顶的 8 折碰上 7999 的手机就是减 1600")
    void 百分比封顶() {
        assertEquals(new BigDecimal("50.00"), percent("20", "50", "7999"));
    }

    @Test
    @DisplayName("🔴 ② 抵扣向下取整，永远不向上 —— 向上是一条不受控的送钱口子")
    void 向下取整() {
        // 33.33 元的 20% = 6.666，取 6.66 而不是 6.67
        assertEquals(new BigDecimal("6.66"), percent("20", "999", "33.33"));
    }

    @Test
    @DisplayName("🔴 抵积分时取整到个位 —— 减 0.5 分这件事钱包那边表达不了")
    void 积分抵扣取整() {
        BigDecimal result = CouponDiscountCalculator.compute(
                CouponDiscountTypeEnum.PERCENT, new BigDecimal("20"), new BigDecimal("999"),
                CouponDeductTargetEnum.SCORE, new BigDecimal("33"));
        // 33 的 20% = 6.6 → 6
        assertEquals(new BigDecimal("6"), result);
    }

    @Test
    @DisplayName("固定额券也被 max_discount 压住：「减 20 但最多减 10」是运营配出来的自相矛盾，取小的")
    void 固定额也受封顶约束() {
        // 取大的那个解释会多付钱，而这里没有任何依据说该多付
        assertEquals(new BigDecimal("10.00"), fixed("20", "10", "150"));
    }

    @Test
    @DisplayName("算出来是负的时候归零，不往外倒贴")
    void 不会算出负数() {
        assertEquals(new BigDecimal("0.00"), fixed("20", null, "0"));
    }

    private static BigDecimal fixed(String value, String max, String pay) {
        return CouponDiscountCalculator.compute(CouponDiscountTypeEnum.FIXED,
                new BigDecimal(value), max == null ? null : new BigDecimal(max),
                CouponDeductTargetEnum.CASH, new BigDecimal(pay));
    }

    private static BigDecimal percent(String rate, String max, String pay) {
        return CouponDiscountCalculator.compute(CouponDiscountTypeEnum.PERCENT,
                new BigDecimal(rate), max == null ? null : new BigDecimal(max),
                CouponDeductTargetEnum.CASH, new BigDecimal(pay));
    }
}
