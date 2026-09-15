package solvela.marketing.api;

import java.math.BigDecimal;

/**
 * 下单 / 支付的结果。
 *
 * <p>🔴 用返回值表达失败，不抛异常 —— 券用不了、单子已取消、场景没开
 * 都是<b>预期内</b>的业务结果。与 {@link MallRedeemResult} 同一套做法。
 *
 * @param reason 拒绝原因码（对齐 {@code RechargeReason}）。
 *               用字符串不用枚举：这个契约将来要跨进程，
 *               而枚举值域的变更在两边不是同时发版的
 */
public record RechargeOrderResult(boolean accepted, String orderNo, BigDecimal payAmount, String reason) {
}
