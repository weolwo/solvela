package solvela.member.api;

import java.math.BigDecimal;

/**
 * 锁定一张券。
 *
 * @param couponId       会员券 id
 * @param memberId       会员号，<b>由调用方从登录态取</b>。
 *                       它进 {@code WHERE} 条件 —— 越权用别人的券这一条就挡住了
 * @param bizType        业务类型，如 {@code MALL}
 * @param bizRefId       单据号。<b>幂等键</b>，同时是确认/释放时的凭据
 * @param sceneCode      外部场景码，商城场景传 null
 * @param payAmount      抵扣<b>前</b>的应付，落进流水
 * @param discountAmount 实际抵扣额。
 *                       🔴 <b>用试算返回的那个数，别自己再算一遍</b> ——
 *                       算两遍得出不同结果是这类代码最典型的资损来源：
 *                       下单页显示减 30，结算时按 20 扣，用户看到的和扣掉的对不上
 */
public record CouponLockCmd(
        Long couponId,
        Long memberId,
        String bizType,
        String bizRefId,
        String sceneCode,
        BigDecimal payAmount,
        BigDecimal discountAmount) {
}
