package solvela.member.session;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 会员会话参数。
 *
 * <pre>
 * solvela:
 *   member:
 *     session:
 *       token-ttl: 30d
 *       max-sessions: 10
 * </pre>
 *
 * <h3>为什么在会员域而不是网关</h3>
 * 这两个值描述的是<b>「一个会员的会话能存在多久、能有几个」</b>，是会员域的规则，
 * 不是某个端的 HTTP 细节。真正属于端的是「令牌放在哪个请求头、带不带 Bearer 前缀」——
 * 那两个仍在 {@code solvela.app.auth} 下。
 *
 * <p>2026-08-30 从 {@code solvela.app.auth} 搬过来。搬的原因不是洁癖：
 * 后台冻结会员时要吊销他的全部会话，而冻结发生在会员域 ——
 * 会话存储留在网关里，会员域就够不着它，于是「冻结即时生效」这件事一直没人实现。
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.session")
public class MemberSessionProperties {

    /**
     * 令牌有效期。C 端的取舍与后台相反：用户不接受频繁登录，所以给得长，
     * 靠「可即时吊销」来兜安全，而不是靠短有效期 —— 而那个前提现在才真正成立。
     *
     * <p>改这个值只影响<b>新签发</b>的令牌，已有令牌的 TTL 不会跟着变。
     */
    private Duration tokenTtl = Duration.ofDays(30);

    /**
     * 单个会员最多同时有效的令牌数，超出时挤掉最旧的。
     *
     * <p>不限制意味着一个被盗号的账号可以攒出无限会话，而 {@code revokeAll} 的成本随之无上限。
     * 小于等于 0 时按默认值处理。
     */
    private int maxSessions = 10;

    public Duration tokenTtl() {
        return tokenTtl == null ? Duration.ofDays(30) : tokenTtl;
    }

    public int maxSessions() {
        return maxSessions <= 0 ? 10 : maxSessions;
    }
}
