package solvela.app.domain;

/**
 * 登录二次验证码已发送。
 *
 * @param maskedTarget 码寄到了哪（打过码的邮箱或手机号）。客户端展示「已发送到 xxx」
 */
public record LoginChallengeCodeView(String maskedTarget) {
}
