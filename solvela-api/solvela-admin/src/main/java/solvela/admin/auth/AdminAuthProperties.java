package solvela.admin.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 管理端认证参数。
 *
 * <p>取代原先 solvela-base.yaml 里那一整段 {@code sa-token:} 配置。那段里的十一个开关，
 * 真正影响本系统行为的只有下面这四条；其余（{@code token-style}、{@code auto-renew}、
 * {@code is-log}、{@code is-print}、{@code is-read-cookie}…）要么是框架内部实现细节，
 * 要么本项目根本没用到——它们的存在只会让人以为「这里可以调」。
 *
 * @param tokenTtl      令牌绝对有效期。后台与 C 端的取舍相反：这里给得短，
 *                      因为一个后台账号能改配置、能发奖、能看会员手机号
 * @param header        携带令牌的请求头
 * @param scheme        令牌前缀（{@code Authorization: Bearer xxx}）；留空表示不带前缀
 * @param singleSession true = 新登录挤掉该账号的旧会话（对应原 {@code is-concurrent: false}）。
 *                      后台默认开：一个账号同时在多处登录，多半意味着账号被共用或被盗，
 *                      而「被挤下线」是使用者能立刻察觉的信号
 */
@ConfigurationProperties(prefix = "solvela.admin.auth")
public record AdminAuthProperties(
        Duration tokenTtl,
        String header,
        String scheme,
        Boolean singleSession) {

    public AdminAuthProperties {
        tokenTtl = tokenTtl == null ? Duration.ofDays(30) : tokenTtl;
        header = header == null || header.isBlank() ? "Authorization" : header;
        scheme = scheme == null ? "Bearer" : scheme;
        singleSession = singleSession == null || singleSession;
    }
}
