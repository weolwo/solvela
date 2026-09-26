package solvela.app.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 二次验证：提交验证码。
 *
 * <p>🔴 刻意没有 memberId、deviceId、email：会员从登录态取，设备从验签过的设备令牌取，
 * 码寄到哪由域按会员号决定。三者任何一个让客户端说了算，这道验证就能被绕过。
 *
 * @param code 发到已绑定邮箱的验证码
 */
public record StepUpVerifyRequest(@NotBlank(message = "请输入验证码") String code) {
}
