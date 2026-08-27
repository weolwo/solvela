package solvela.app.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 不透明令牌（opaque token），落在 Redis 上。
 *
 * <h3>为什么不是 JWT</h3>
 * JWT 的卖点是「服务端不用查存储」，代价是<b>签发之后就收不回来</b>。
 * 而 C 端最要紧的一条运营需求恰恰是「后台点冻结，用户当场用不了」——
 * 上一版靠 30 分钟的身份缓存兜这件事，也就是被冻结的账号最长还能正常用半小时。
 * 想用 JWT 又要即时吊销，就得回来查一次黑名单，那 JWT 省下的那次查询也就没了，
 * 还多背一份「令牌里的信息可能已经过期」的心智负担。
 *
 * <p>所以这里选不透明令牌：一次 Redis GET 换来即时吊销、按设备下线、
 * 以及「令牌里什么信息都没有」——被截获也推不出会员号。
 *
 * <h3>为什么存摘要而不是令牌本身</h3>
 * Redis 里存的是 {@code SHA-256(token)}。这样一来：
 * <ul>
 *   <li>Redis 被 dump、被误导出、被运维 {@code KEYS *} 打印到终端 —— 拿到的都是摘要，
 *       <b>不能拿去登录</b>；</li>
 *   <li>慢查询日志、监控采样里出现 key，也不构成泄露。</li>
 * </ul>
 * 密码要哈希存的道理，对「等价于密码的长期凭证」同样成立。上一版（sa-token）
 * 是把 token 原文当 key 存的。
 *
 * <h3>两组 key</h3>
 * <pre>
 *   app:auth:t:{摘要}      -> memberId        正查，每个请求一次
 *   app:auth:m:{memberId}  -> Set&lt;摘要&gt;   反查，用于「退出所有设备」和挤号
 * </pre>
 * 反查集合是<b>可以有脏数据的</b>：令牌到期自然消失，集合里的摘要不会跟着删。
 * 这不影响正确性（吊销一个已过期的摘要是空操作），清理由每次写入时的裁剪顺带完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    /** 令牌前缀。看一眼就知道是哪个系统的凭证，也让「把后台 token 贴到 C 端」这种事在第一步就失败。 */
    private static final String TOKEN_PREFIX = "mb_";

    private static final String KEY_TOKEN = "app:auth:t:";
    private static final String KEY_MEMBER = "app:auth:m:";

    /** 32 字节 = 256 bit 熵。够到「穷举不可行」那一档，再长只是让请求头更胖。 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    @Override
    public AccessToken issue(Long memberId) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String value = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String digest = digest(value);

        redis.opsForValue().set(KEY_TOKEN + digest, String.valueOf(memberId), properties.tokenTtl());

        String memberKey = KEY_MEMBER + memberId;
        redis.opsForSet().add(memberKey, digest);
        redis.expire(memberKey, properties.tokenTtl());
        trimSessions(memberId, memberKey);

        return new AccessToken(value, properties.tokenTtl());
    }

    @Override
    public Long resolve(String tokenValue) {
        if (tokenValue == null || !tokenValue.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String memberId = redis.opsForValue().get(KEY_TOKEN + digest(tokenValue));
        if (memberId == null) {
            return null;
        }
        try {
            return Long.valueOf(memberId);
        } catch (NumberFormatException e) {
            // 只可能是有人手改了 Redis，或 key 撞了别的系统。删掉，让用户重新登录。
            log.warn("[Auth] 令牌指向的 memberId 不是数字，已清理：{}", memberId);
            redis.delete(KEY_TOKEN + digest(tokenValue));
            return null;
        }
    }

    @Override
    public void revoke(String tokenValue) {
        if (tokenValue == null || !tokenValue.startsWith(TOKEN_PREFIX)) {
            return;
        }
        String digest = digest(tokenValue);
        String memberId = redis.opsForValue().get(KEY_TOKEN + digest);
        redis.delete(KEY_TOKEN + digest);
        if (memberId != null) {
            redis.opsForSet().remove(KEY_MEMBER + memberId, digest);
        }
    }

    @Override
    public int revokeAll(Long memberId) {
        String memberKey = KEY_MEMBER + memberId;
        Set<String> digests = redis.opsForSet().members(memberKey);
        if (digests == null || digests.isEmpty()) {
            redis.delete(memberKey);
            return 0;
        }
        redis.delete(digests.stream().map(d -> KEY_TOKEN + d).toList());
        redis.delete(memberKey);
        return digests.size();
    }

    /**
     * 超出 maxSessions 时挤掉最旧的会话。
     *
     * <p>「最旧」靠 Redis 的剩余 TTL 判断 —— 令牌的有效期都一样长，所以剩得最少的就是签得最早的。
     * 这比另存一份签发时间省一次写入，代价是精度只到秒，而这里不需要更准。
     */
    private void trimSessions(Long memberId, String memberKey) {
        Set<String> digests = redis.opsForSet().members(memberKey);
        if (digests == null || digests.size() <= properties.maxSessions()) {
            return;
        }
        record Session(String digest, long ttl) {
        }
        List<Session> alive = new java.util.ArrayList<>();
        Set<String> dead = new HashSet<>();
        for (String d : digests) {
            Long ttl = redis.getExpire(KEY_TOKEN + d);
            // -2 = key 不存在（令牌已自然过期），集合里的这条是残留，顺手清掉
            if (ttl == null || ttl < 0) {
                dead.add(d);
            } else {
                alive.add(new Session(d, ttl));
            }
        }
        if (!dead.isEmpty()) {
            redis.opsForSet().remove(memberKey, dead.toArray());
        }
        int excess = alive.size() - properties.maxSessions();
        if (excess <= 0) {
            return;
        }
        alive.sort(java.util.Comparator.comparingLong(Session::ttl));
        for (int i = 0; i < excess; i++) {
            String d = alive.get(i).digest();
            redis.delete(KEY_TOKEN + d);
            redis.opsForSet().remove(memberKey, d);
        }
        log.info("[Auth] 会员 {} 会话数超限，挤掉最旧的 {} 个", memberId, excess);
    }

    private static String digest(String tokenValue) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须实现的算法，走到这里说明 JRE 被裁剪过
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", e);
        }
    }
}
