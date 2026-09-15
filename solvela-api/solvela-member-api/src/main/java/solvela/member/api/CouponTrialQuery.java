package solvela.member.api;

import java.math.BigDecimal;

/**
 * 试算入参：<b>这一单要付什么</b>。
 *
 * <h3>🔴 两个应付都传，不要在入口先挑一个抵扣对象</h3>
 * 2026-09-15 改。此前这里只收<b>一个</b> {@code payAmount} + 一个
 * {@code deductTarget}，于是混合支付单（积分 + 现金）必须在入口二选一 ——
 * 商城为此定了条「混合单只抵现金」的规则，把 {@code SCORE} 券挡在了门外。
 *
 * <p>那条规则站不住：<b>一单一券</b>下只有一张券，它自己的 {@code deduct_target}
 * 就唯一决定了抵哪一半。不可比的是<b>推荐</b>（10 积分和 5 块钱谁更划算答不了），
 * 不是<b>可用性</b> —— 所以结果按抵扣对象分组，但两种券都参与试算。
 *
 * @param memberId     会员号，<b>由调用方从登录态取</b>
 * @param payPoints    抵扣<b>前</b>的应付积分。{@code null} 或 0 = 这一单没有积分部分
 * @param payCash      抵扣<b>前</b>的应付现金。{@code null} 或 0 = 这一单没有现金部分
 * @param commodityRef 商品标识，{@code COMMODITY} 范围的券按它匹配
 * @param categoryRef  类目标识，{@code CATEGORY} 范围的券按它匹配
 * @param sceneCode    外部场景码（如 {@code MOBILE_RECHARGE}），{@code EXTERNAL} 范围的券按它匹配
 */
public record CouponTrialQuery(
        Long memberId,
        BigDecimal payPoints,
        BigDecimal payCash,
        String commodityRef,
        String categoryRef,
        String sceneCode) {
}
