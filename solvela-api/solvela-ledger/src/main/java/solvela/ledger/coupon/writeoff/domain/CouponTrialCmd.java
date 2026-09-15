package solvela.ledger.coupon.writeoff.domain;

import solvela.enums.CouponDeductTargetEnum;

import java.math.BigDecimal;

/**
 * 试算入参：<b>这一单要付什么</b>。
 *
 * <h3>🔴 两个应付都传进来，而不是让调用方先挑一个抵扣对象</h3>
 * 2026-09-15 改。此前这里只收<b>一个</b> {@code payAmount} + 一个
 * {@code deductTarget}，于是混合支付单（积分 + 现金）必须在入口二选一 ——
 * 商城那边因此定了条「混合单只抵现金」的规则，把 {@code SCORE} 券挡在了门外。
 *
 * <p>那条规则站不住：<b>一单一券</b>的前提下只有一张券，
 * 它自己的 {@code deduct_target} 就唯一决定了抵哪一半，
 * 根本不存在「先抵哪一部分」这个问题。当时的理由是把<b>叠加</b>场景的顾虑
 * 套到了非叠加场景上。
 *
 * <p>真正不可比的是<b>推荐</b>（10 积分和 5 块钱谁更划算答不了），
 * 不是<b>可用性</b>。所以分组推荐（见 {@link CouponTrialResult}），
 * 但两种券都照常参与试算。
 *
 * @param memberId     谁在下单
 * @param payPoints    抵扣<b>前</b>的应付积分。{@code null} 或 0 = 这一单没有积分部分
 * @param payCash      抵扣<b>前</b>的应付现金。{@code null} 或 0 = 这一单没有现金部分
 * @param commodityRef 商品标识。{@code COMMODITY} 范围的券按它匹配，非商城场景传 null
 * @param categoryRef  类目标识。{@code CATEGORY} 范围的券按它匹配
 * @param sceneCode    外部场景码（如 {@code MOBILE_RECHARGE}）。{@code EXTERNAL} 范围的券按它匹配
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record CouponTrialCmd(Long memberId,
                             BigDecimal payPoints,
                             BigDecimal payCash,
                             String commodityRef,
                             String categoryRef,
                             String sceneCode) {

    /**
     * 这张券能抵的那一部分应付是多少。
     *
     * @return {@code null} 表示这一单<b>没有</b>它能抵的那部分
     *         （纯积分单上的现金券、纯现金单上的积分券）
     */
    public BigDecimal payableFor(CouponDeductTargetEnum deductTarget) {
        if (deductTarget == null) {
            return null;
        }
        return deductTarget == CouponDeductTargetEnum.SCORE ? payPoints : payCash;
    }

    /** 商城场景。纯积分商品把 {@code payCash} 传 null 即可 */
    public static CouponTrialCmd forMall(Long memberId, BigDecimal payPoints, BigDecimal payCash,
                                         String commodityRef, String categoryRef) {
        return new CouponTrialCmd(memberId, payPoints, payCash, commodityRef, categoryRef, null);
    }

    /** 外部场景（充话费这类），只有现金 */
    public static CouponTrialCmd forScene(Long memberId, BigDecimal payCash, String sceneCode) {
        return new CouponTrialCmd(memberId, null, payCash, null, null, sceneCode);
    }
}
