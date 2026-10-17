package solvela.app.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关限流的进程级参数。阈值与阶梯<b>不在这里</b>（它们在库里、经快照下发、后台可改）；
 * 这里只有「必须跟着部署走」的两样。
 *
 * <pre>
 * solvela:
 *   app:
 *     rate-limit:
 *       enabled: true
 *       account-key: ${SOLVELA_APP_RATE_LIMIT_ACCOUNT_KEY}
 * </pre>
 *
 * @param enabled    总开关。关掉等于网关不限流 —— 只用于误伤排障时的临时回退，要发版
 * @param accountKey 账号维度计数键的 HMAC 密钥。匿名接口的「账号」是邮箱 / 手机号，直接拿明文当 Redis 键等于把它们写进了 Redis；
 *                   裸 SHA-256 也不行 —— 手机号的取值空间小，能被穷举还原。
 *                   <p>🔴 不配（或短于 32 位）启动即失败，理由同设备令牌的密钥：给了默认值，忘配置的人会用一个全世界都知道的密钥。
 *                   <p>它<b>可以随时换</b>：换了只是账号维度的计数从零开始，不影响任何数据 —— 所以不需要设备密钥那套版本轮换。
 *                   同一环境的多个网关实例必须一致，否则同一个账号在各实例上各数各的。
 * @Date 2026-10-17
 */
@ConfigurationProperties(prefix = "solvela.app.rate-limit")
public record RateLimitProperties(Boolean enabled, String accountKey) {

    static final int MIN_KEY_LENGTH = 32;

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        if (enabled && (accountKey == null || accountKey.length() < MIN_KEY_LENGTH)) {
            throw new IllegalStateException("solvela.app.rate-limit.account-key 未配置或短于 " + MIN_KEY_LENGTH
                    + " 位：账号维度的计数键会用它算 HMAC（生产从环境变量 SOLVELA_APP_RATE_LIMIT_ACCOUNT_KEY 注入）");
        }
    }
}
