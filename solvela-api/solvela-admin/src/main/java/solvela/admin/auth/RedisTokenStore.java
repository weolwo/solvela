package solvela.admin.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * 不透明令牌（opaque token），落在 Redis 上。
 *
 * <h3>为什么存摘要而不是令牌本身</h3>
 * Redis 里存的是 {@code SHA-256(token)}。这样一来：
 * <ul>
 *   <li>Redis 被 dump、被误导出、被运维 {@code KEYS *} 打印到终端 —— 拿到的都是摘要，
 *       <b>不能拿去登录</b>；</li>
 *   <li>慢查询日志、监控采样里出现 key，也不构成泄露。</li>
 * </ul>
 * 密码要哈希存的道理，对「等价于密码的长期凭证」同样成立。
 * sa-token 是把 token 原文当 key 存的 —— 一个后台令牌等于一个能改系统配置的身份，
 * 明文躺在 Redis 里的风险比 C 端更高。
 *
 * <h3>三组 key</h3>
 * <pre>
 *   admin:auth:t:{摘要}   -> AdminSession(JSON)   TTL = 绝对有效期
 *   admin:auth:a:{摘要}   -> "1"                  TTL = 最低活跃频率，每次请求续期
 *   admin:auth:e:{员工id} -> Set&lt;摘要&gt;        反查，用于挤号与「禁用即下线」
 * </pre>
 *
 * <p><b>为什么活跃期要单独一个 key，而不是给 t 续期</b>：给 t 续期就变成了滑动过期，
 * 令牌可以被无限续下去，「绝对有效期」这条线就没了 —— 一个泄露的令牌只要有人持续访问
 * 就永不失效。分成两个 key 之后两条线各管各的：t 到期是硬上限，a 到期是活跃判定。
 *
 * <p>反查集合允许有脏数据：令牌到期自然消失，集合里的摘要不会跟着删。
 * 这不影响正确性（吊销一个已过期的摘要是空操作），也不影响安全性（判定只看 t）。
 */
@Slf4j
@Component
public class RedisTokenStore implements TokenStore {

    /**
     * 令牌前缀。看一眼就知道是哪个系统的凭证，也让「把 C 端 token 贴到后台」
     * 这种事在第一步就失败 —— C 端用的是 {@code mb_}。
     */
    private static final String TOKEN_PREFIX = "ad_";

    private static final String KEY_TOKEN = "admin:auth:t:";
    private static final String KEY_ACTIVE = "admin:auth:a:";
    private static final String KEY_EMPLOYEE = "admin:auth:e:";

    /** 32 字节 = 256 bit 熵。够到「穷举不可行」那一档，再长只是让请求头更胖。 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AdminAuthProperties properties;

    /**
     * 最低活跃频率（秒），{@code <= 0} 不限制。
     *
     * <p>volatile：写它的是等保配置的保存请求，读它的是每一个业务请求 ——
     * 两个不同的线程，不加 volatile 的话配置改完可能很久都不生效，而且完全没有报错。
     */
    private volatile int activeTimeoutSeconds = -1;

    public RedisTokenStore(StringRedisTemplate redis, AdminAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public AccessToken issue(Long employeeId, boolean superFlag, String device) {
        return issue(employeeId, superFlag, device, properties.tokenTtl());
    }

    @Override
    public AccessToken issue(Long employeeId, boolean superFlag, String device, Duration ttl) {
        if (Boolean.TRUE.equals(properties.singleSession())) {
            // 挤号：先把旧会话清干净再发新的。顺序不能反 —— 反了会把刚发的这个也吊销掉
            revokeAll(employeeId);
        }

        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String value = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String digest = digest(value);

        redis.opsForValue().set(KEY_TOKEN + digest,
                JsonUtils.toJson(new AdminSession(employeeId, superFlag, device)), ttl);
        touchActive(digest);

        String employeeKey = KEY_EMPLOYEE + employeeId;
        redis.opsForSet().add(employeeKey, digest);
        // 反查集合的生命周期跟着最长的那个令牌走；晚于任何一个令牌过期都只是留下无害的脏数据
        redis.expire(employeeKey, ttl);

        return new AccessToken(value, ttl);
    }

    @Override
    public TokenLookup lookup(String tokenValue) {
        if (tokenValue == null || !tokenValue.startsWith(TOKEN_PREFIX)) {
            return TokenLookup.unknown();
        }
        String digest = digest(tokenValue);
        String json = redis.opsForValue().get(KEY_TOKEN + digest);
        if (json == null) {
            return TokenLookup.unknown();
        }

        int timeout = activeTimeoutSeconds;
        if (timeout > 0 && Boolean.FALSE.equals(redis.hasKey(KEY_ACTIVE + digest))) {
            // 令牌还在，但人太久没动了。连令牌一起吊销 —— 否则「重新登录」之后
            // 旧令牌仍然可用，等保那条「长时间未操作需重新认证」就只是个弹窗
            revoke(tokenValue);
            return TokenLookup.inactive();
        }

        AdminSession session = JsonUtils.parseObject(json, AdminSession.class);
        if (session == null || session.employeeId() == null) {
            // 只可能是有人手改了 Redis，或 key 撞了别的系统。删掉，让用户重新登录
            log.warn("[AdminAuth] 会话内容无法解析，已清理。json={}", json);
            revoke(tokenValue);
            return TokenLookup.unknown();
        }

        touchActive(digest);
        return new TokenLookup.Authenticated(session);
    }

    @Override
    public void revoke(String tokenValue) {
        if (tokenValue == null || !tokenValue.startsWith(TOKEN_PREFIX)) {
            return;
        }
        String digest = digest(tokenValue);
        String json = redis.opsForValue().get(KEY_TOKEN + digest);
        redis.delete(KEY_TOKEN + digest);
        redis.delete(KEY_ACTIVE + digest);
        if (json == null) {
            return;
        }
        AdminSession session = JsonUtils.parseObject(json, AdminSession.class);
        if (session != null && session.employeeId() != null) {
            redis.opsForSet().remove(KEY_EMPLOYEE + session.employeeId(), digest);
        }
    }

    @Override
    public int revokeAll(Long employeeId) {
        if (employeeId == null) {
            return 0;
        }
        String employeeKey = KEY_EMPLOYEE + employeeId;
        Set<String> digests = redis.opsForSet().members(employeeKey);
        if (digests == null || digests.isEmpty()) {
            redis.delete(employeeKey);
            return 0;
        }
        // 先删令牌再删集合：反过来的话，中途失败会留下一批「集合里没有、但仍然能登录」的令牌，
        // 那种令牌再也没有办法被吊销
        for (String digest : digests) {
            redis.delete(KEY_TOKEN + digest);
            redis.delete(KEY_ACTIVE + digest);
        }
        redis.delete(employeeKey);
        return digests.size();
    }

    @Override
    public void setActiveTimeoutSeconds(int seconds) {
        this.activeTimeoutSeconds = seconds;
    }

    /**
     * 续活跃期。不限制时<b>不写这个 key</b> —— 写一个永不过期的 key 只会在 Redis 里
     * 攒下和令牌一样多的垃圾，而判定分支根本不会读它。
     */
    private void touchActive(String digest) {
        int timeout = activeTimeoutSeconds;
        if (timeout > 0) {
            redis.opsForValue().set(KEY_ACTIVE + digest, "1", Duration.ofSeconds(timeout));
        }
    }

    private static String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须实现的算法，走不到这里
            throw new IllegalStateException(e);
        }
    }
}
