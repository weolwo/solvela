package solvela.app.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * C 端<b>怎么携带令牌</b>的参数。只有 HTTP 层面的两件事。
 *
 * <p>令牌活多久、一个会员能有几个会话，已于 2026-08-30 搬到会员域的
 * {@code MemberSessionProperties}（{@code solvela.member.session.*}）——
 * 那两个是会员的规则，而后台冻结会员时要吊销他的全部会话，会员域必须够得着会话存储。
 * 留在这里的是真正属于端的：令牌放哪个头、带不带前缀。
 *
 * @param header 携带令牌的请求头
 * @param scheme 令牌前缀（{@code Authorization: Bearer xxx}）；留空表示不带前缀
 */
@ConfigurationProperties(prefix = "solvela.app.auth")
public record AuthProperties(String header, String scheme) {

    public AuthProperties {
        header = header == null || header.isBlank() ? "Authorization" : header;
        scheme = scheme == null ? "Bearer" : scheme;
    }
}
