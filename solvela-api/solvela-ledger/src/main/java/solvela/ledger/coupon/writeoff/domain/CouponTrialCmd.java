package solvela.ledger.coupon.writeoff.domain;

import solvela.enums.CouponDeductTargetEnum;

import java.math.BigDecimal;

/**
 * 试算入参：<b>这一单长什么样</b>。
 *
 * @param memberId     谁在下单
 * @param payAmount    抵扣<b>前</b>的应付。券能减多少是按它算的，
 *                     而且算出来的抵扣<b>不能超过它</b>（见 {@code CouponWriteOffService}）
 * @param deductTarget 这一单付的是什么：{@code CASH} 还是 {@code SCORE}。
 *                     🔴 不能为空 —— 抵现金的券和抵积分的券<b>不可比</b>，
 *                     没有这个字段就只能替用户跨类猜，而 1 积分 ≠ 1 元
 * @param commodityRef 商品标识。{@code COMMODITY} 范围的券按它匹配，非商城场景传 null
 * @param categoryRef  类目标识。{@code CATEGORY} 范围的券按它匹配
 * @param sceneCode    外部场景码（如 {@code MOBILE_RECHARGE}）。{@code EXTERNAL} 范围的券按它匹配
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record CouponTrialCmd(Long memberId,
                             BigDecimal payAmount,
                             CouponDeductTargetEnum deductTarget,
                             String commodityRef,
                             String categoryRef,
                             String sceneCode) {

    /** 商城场景的快捷构造 */
    public static CouponTrialCmd forMall(Long memberId, BigDecimal payAmount,
                                         CouponDeductTargetEnum deductTarget,
                                         String commodityRef, String categoryRef) {
        return new CouponTrialCmd(memberId, payAmount, deductTarget, commodityRef, categoryRef, null);
    }

    /** 外部场景（充话费这类）的快捷构造 */
    public static CouponTrialCmd forScene(Long memberId, BigDecimal payAmount,
                                          CouponDeductTargetEnum deductTarget, String sceneCode) {
        return new CouponTrialCmd(memberId, payAmount, deductTarget, null, null, sceneCode);
    }
}
