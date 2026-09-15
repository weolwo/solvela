package solvela.app.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 充值下单请求。
 *
 * <h3>🔴 刻意没有 memberId，也没有「抵扣多少」</h3>
 * 会员号由控制器从登录态取 —— 有了那个参数就是「用别人的券给自己充话费」。
 * 抵扣额由服务端重新试算：让客户端报数就是一个可以直接刷钱的口子。
 *
 * @param targetAccount 充值手机号。明文传，服务端落库时加密
 * @param amount        面额。服务端只认白名单里的那几个
 * @param couponId      用哪张券，不用券不传
 *
 * @Author alaric
 * @Date 2026-09-15
 */
public record RechargeRequest(
        @NotBlank(message = "请填写手机号") String targetAccount,
        @NotNull(message = "请选择充值面额") BigDecimal amount,
        Long couponId) {
}
