package solvela.external.domain;

import java.math.BigDecimal;

/**
 * 充值下单。
 *
 * @param memberId      会员号，<b>由调用方从登录态取</b>。客户端传的一律不认
 * @param targetAccount 充值目标手机号。<b>明文进来，落库时加密</b>
 * @param amount        面额。🔴 只认白名单里的那几个 —— 开放任意金额的话，
 *                      一个「充 0.01 元」的请求会试图把一张满 100 减 10 的券
 *                      套进一笔一分钱的单子里
 * @param couponId      要用的券，不用券传 null。
 *                      <p>🔴 <b>刻意没有「抵扣多少」</b>：抵扣额由服务端重新试算。
 *                      让客户端报数就是一个可以直接刷钱的口子
 *
 * @Author alaric
 * @Date 2026-09-15
 */
public record RechargeCmd(Long memberId, String targetAccount, BigDecimal amount, Long couponId) {
}
