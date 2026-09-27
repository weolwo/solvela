package solvela.app.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录二次验证（观察档设备）：凭票发码 / 验码。
 *
 * <p>🔴 刻意没有手机号、邮箱、密码：码发到哪、验哪个身份，全由票决定（服务端签票时记下的）。
 *
 * @param ticket    {@code /auth/login} 回 DEVICE_VERIFICATION_REQUIRED 时 details.challengeTicket 里的那张票
 * @param code      验证码，只有验码那一步要
 * @param useCookie 同登录：Web 端传 true，令牌只经 HttpOnly cookie 下发
 * @param remember  同登录的「记住我」
 */
public record LoginChallengeRequest(@NotBlank(message = "验证已过期，请重新登录") String ticket,
                                    String code, Boolean useCookie, Boolean remember) {

    public boolean cookieDelivery() {
        return Boolean.TRUE.equals(useCookie);
    }

    public boolean rememberMe() {
        return Boolean.TRUE.equals(remember);
    }
}
