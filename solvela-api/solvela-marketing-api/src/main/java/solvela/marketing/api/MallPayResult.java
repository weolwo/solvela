package solvela.marketing.api;

/**
 * 支付结果。
 *
 * <h3>🔴 用返回值表达失败，不抛异常</h3>
 * 「单子已被超时取消」「已经付过了」都是<b>预期内</b>的业务结果，不是故障。
 * 抛出去的话跨进程之后一律变成 5xx，监控上会多出一堆假的服务端错误。
 * 与 {@link MallRedeemResult} 同一套做法。
 *
 * @param accepted 付掉了没有。为 true 时 {@link #reason} 必为 null
 * @param orderNo  订单号，回显给端上
 * @param reason   没付掉的原因
 *
 * @Author alaric
 * @Date 2026-09-15
 */
public record MallPayResult(boolean accepted, String orderNo, MallPayReason reason) {

    public static MallPayResult ofAccepted(String orderNo) {
        return new MallPayResult(true, orderNo, null);
    }

    public static MallPayResult ofReject(MallPayReason reason) {
        return new MallPayResult(false, null, reason);
    }
}
