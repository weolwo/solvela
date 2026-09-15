package solvela.external.domain;

import java.math.BigDecimal;

/**
 * 充值下单 / 支付的结果。
 *
 * <h3>🔴 用返回值表达失败，不抛异常</h3>
 * 券用不了、单子已被取消、场景没开 —— 都是<b>预期内</b>的业务结果，不是故障。
 * 与 {@code MallRedeemResult} / {@code MallPayResult} 同一套做法。
 *
 * @param accepted  受理了没有
 * @param orderNo   单号
 * @param payAmount 实付金额（抵扣后）
 * @param reason    没受理的原因
 *
 * @Author alaric
 * @Date 2026-09-15
 */
public record RechargeResult(boolean accepted, String orderNo, BigDecimal payAmount, RechargeReason reason) {

    public static RechargeResult ofAccepted(String orderNo, BigDecimal payAmount) {
        return new RechargeResult(true, orderNo, payAmount, null);
    }

    public static RechargeResult ofReject(RechargeReason reason) {
        return new RechargeResult(false, null, null, reason);
    }
}
