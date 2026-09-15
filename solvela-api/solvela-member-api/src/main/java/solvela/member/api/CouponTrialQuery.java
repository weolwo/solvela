package solvela.member.api;

import java.math.BigDecimal;

/**
 * 试算入参：这一单长什么样。
 *
 * @param memberId     会员号，<b>由调用方从登录态取</b>
 * @param payAmount    抵扣<b>前</b>的应付。券能减多少按它算，而且算出来的抵扣
 *                     <b>不能超过它</b>
 * @param deductTarget 这一单付的是什么：{@code CASH} / {@code SCORE}。
 *                     🔴 不能为空 —— 抵现金的券和抵积分的券<b>不可比</b>
 *                     （1 积分 ≠ 1 元，汇率是业务定义还会变），
 *                     没有这个字段就只能替用户跨类猜。
 *                     用字符串不用枚举：这个契约将来要跨进程，
 *                     而枚举值域的变更在两边不是同时发版的
 * @param commodityRef 商品标识，{@code COMMODITY} 范围的券按它匹配
 * @param categoryRef  类目标识，{@code CATEGORY} 范围的券按它匹配
 * @param sceneCode    外部场景码（如 {@code MOBILE_RECHARGE}），{@code EXTERNAL} 范围的券按它匹配
 */
public record CouponTrialQuery(
        Long memberId,
        BigDecimal payAmount,
        String deductTarget,
        String commodityRef,
        String categoryRef,
        String sceneCode) {
}
