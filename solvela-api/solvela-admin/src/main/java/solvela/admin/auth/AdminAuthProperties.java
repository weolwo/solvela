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
 * @param header        携带令牌的请求头。默认 X-Access-Token（不用 Authorization，
 *                      那个头被入口的 nginx Basic Auth 占着，见构造器里的说明）
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
        // 🔴 默认【不用 Authorization】：那个头被 HTTP Basic Auth 占着。
        //    管理端上线时前面加了一层 nginx Basic Auth 作为网络级闸门（见 deploy/nginx），
        //    Basic Auth 用的就是 Authorization 头。若 app 的 token 也放 Authorization，
        //    登录后 admin-web 每个请求都用 token 覆盖掉浏览器的 Basic 凭据 ——
        //    nginx 验不过、回 401、浏览器又弹框，成死循环（2026-09-12 实测踩到）。
        //    换一个自己的头就两不相干。⚠️ 改这里必须同步改 admin-web 的 TOKEN_HEADER。
        header = header == null || header.isBlank() ? "X-Access-Token" : header;
        scheme = scheme == null ? "Bearer" : scheme;
        singleSession = singleSession == null || singleSession;
    }
}
