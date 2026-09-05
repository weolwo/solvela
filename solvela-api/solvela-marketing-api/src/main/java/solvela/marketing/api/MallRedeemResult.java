package solvela.marketing.api;

import solvela.enums.MallOrderStatusEnum;

/**
 * 兑换结果。
 *
 * <p>没被受理时用<b>返回值</b>表达，不抛异常：库存没了、超限兑、积分不足
 * 全都是预期内的业务结果，抛出去跨进程后一律变成 5xx，
 * 监控上会多出一堆假的服务端错误。与 {@code DrawResultView} 同一套做法。
 *
 * @param orderNo 服务端生成的订单号。<b>它同时是扣积分的幂等键</b>
 * @param status  受理后订单落在哪个状态：纯积分 → PENDING(待履约)；
 *                积分+现金 → UNPAID(待支付)
 */
public record MallRedeemResult(
        boolean accepted,
        String orderNo,
        MallOrderStatusEnum status,
        MallRedeemReason reason) {

    public static MallRedeemResult ofAccepted(String orderNo, MallOrderStatusEnum status) {
        return new MallRedeemResult(true, orderNo, status, null);
    }

    public static MallRedeemResult ofReject(MallRedeemReason reason) {
        return new MallRedeemResult(false, null, null, reason);
    }
}
