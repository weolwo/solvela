package solvela.app.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * C 端认证参数。
 *
 * @param tokenTtl     令牌有效期。C 端的取舍与后台相反：用户不接受频繁登录，
 *                     所以给得长，靠「可即时吊销」来兜安全，而不是靠短有效期。
 * @param header       携带令牌的请求头
 * @param scheme       令牌前缀（{@code Authorization: Bearer xxx}）；留空表示不带前缀
 * @param maxSessions  单个会员最多同时有效的令牌数，超出时挤掉最旧的。
 *                     0 表示不限制 —— 但不限制意味着一个被盗号的账号可以攒出无限会话，
 *                     且 revokeAll 的成本随之无上限。
 */
@ConfigurationProperties(prefix = "solvela.app.auth")
public record AuthProperties(
        Duration tokenTtl,
        String header,
        String scheme,
        int maxSessions) {

    public AuthProperties {
        tokenTtl = tokenTtl == null ? Duration.ofDays(30) : tokenTtl;
        header = header == null || header.isBlank() ? "Authorization" : header;
        scheme = scheme == null ? "Bearer" : scheme;
        maxSessions = maxSessions <= 0 ? 10 : maxSessions;
    }
}
