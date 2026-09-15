package solvela.marketing.api;

import java.math.BigDecimal;

/**
 * 充值下单。
 *
 * @param memberId      会员号，<b>由调用方从登录态取</b>
 * @param targetAccount 充值手机号。明文传，服务端落库时加密
 * @param amount        面额，只认白名单
 * @param couponId      用哪张券，不用传 null。
 *                      🔴 <b>刻意没有「抵扣多少」</b>：抵扣额由服务端重新试算
 */
public record RechargeOrderCmd(Long memberId, String targetAccount, BigDecimal amount, Long couponId) {
}
