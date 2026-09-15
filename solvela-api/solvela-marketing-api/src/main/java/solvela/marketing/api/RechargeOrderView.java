package solvela.marketing.api;

import java.math.BigDecimal;

/**
 * 一条充值单（C 端形状）。
 *
 * @param targetMasked   打码后的手机号。🔴 <b>明文不出网关</b>
 * @param couponDiscount 券抵扣了多少，没用券时为 null
 * @param statusDesc     状态的人话版本
 * @param payable        能不能点「去支付」。<b>由服务端判</b>，端上不按状态自己推
 */
public record RechargeOrderView(String orderNo,
                                String targetMasked,
                                BigDecimal originalAmount,
                                BigDecimal couponDiscount,
                                BigDecimal payAmount,
                                Integer status,
                                String statusDesc,
                                boolean payable,
                                String createTime) {
}
