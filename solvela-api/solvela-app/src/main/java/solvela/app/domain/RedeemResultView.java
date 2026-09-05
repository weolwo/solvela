package solvela.app.domain;

/**
 * 兑换受理后的结果。
 *
 * @param orderNo 订单号。<b>展示给用户</b>：出问题时他报这个给客服，比让客服去按时间翻表快得多
 * @param status  订单状态码，对齐 {@code MallOrderStatusEnum} 的数值 ——
 *                <b>数值本身就是契约</b>，前端不做名称映射
 * @param message 给用户看的一句话。⚠️ 现金部分没有支付链路，
 *                所以 payType=2 的单会如实说「待支付」，不说「兑换成功」——
 *                说成功的话用户会等着收货，而那单永远不会被履约
 */
public record RedeemResultView(String orderNo, int status, String message) {
}
