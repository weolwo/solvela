package solvela.app.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.member.session.MemberAccessToken;
import solvela.member.session.MemberTokenStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 令牌存储的行为，打真 Redis。
 *
 * <p>重点不是「签发能用」——那个一跑就知道。重点是三条<b>坏掉也不报错</b>的性质：
 * 令牌原文不落库、令牌一定带过期时间、吊销是真的吊销。
 */
@SpringBootTest
class TokenStoreTest {

    private static final Long MEMBER_ID = 999_000_001L;

    @Autowired
    private MemberTokenStore tokenStore;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("签发的令牌能换回会员号，且带过期时间")
    void 签发与解析() {
        MemberAccessToken token = tokenStore.issue(MEMBER_ID);
        try {
            assertTrue(token.value().startsWith("mb_"), "令牌应带系统前缀，实际：" + token.value());
            assertEquals(MEMBER_ID, tokenStore.resolve(token.value()));

            Long ttl = redis.getExpire(tokenKey(token.value()), TimeUnit.SECONDS);
            assertNotNull(ttl);
            assertTrue(ttl > 0, "令牌没有过期时间，它会永远留在 Redis 里，实际 TTL = " + ttl);
        } finally {
            tokenStore.revoke(token.value());
        }
    }

    @Test
    @DisplayName("🔴 Redis 里存的是摘要，不是令牌原文")
    void 令牌原文不落库() {
        MemberAccessToken token = tokenStore.issue(MEMBER_ID);
        try {
            // 原文当 key 查不到 —— 说明存的不是它
            assertNull(redis.opsForValue().get("app:auth:t:" + token.value()));
            // 摘要当 key 查得到
            assertEquals(String.valueOf(MEMBER_ID), redis.opsForValue().get(tokenKey(token.value())));

            // 反查集合里也只有摘要
            Set<String> digests = redis.opsForSet().members("app:auth:m:" + MEMBER_ID);
            assertNotNull(digests);
            assertFalse(digests.contains(token.value()),
                    "反查集合里出现了令牌原文。Redis 被 dump、被 KEYS * 打印、进了慢查询日志，"
                            + "拿到的就是可以直接登录的凭证 —— 等价于明文存密码。");
        } finally {
            tokenStore.revoke(token.value());
        }
    }

    @Test
    @DisplayName("吊销之后立刻失效 —— 这是选不透明令牌而不是 JWT 的全部理由")
    void 吊销即时生效() {
        MemberAccessToken token = tokenStore.issue(MEMBER_ID);
        assertEquals(MEMBER_ID, tokenStore.resolve(token.value()));

        tokenStore.revoke(token.value());
        assertNull(tokenStore.resolve(token.value()),
                "吊销后仍然能解析出会员号。后台点『冻结』时用户不会当场掉线，"
                        + "而这正是当初不选 JWT 的原因。");

        // 幂等：再吊销一次不该炸
        assertDoesNotThrow(() -> tokenStore.revoke(token.value()));
    }

    @Test
    @DisplayName("退出所有设备：多个令牌一起失效")
    void 全部吊销() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            tokens.add(tokenStore.issue(MEMBER_ID).value());
        }
        try {
            int revoked = tokenStore.revokeAll(MEMBER_ID);
            assertTrue(revoked >= 3, "应至少吊销 3 个，实际 " + revoked);
            for (String t : tokens) {
                assertNull(tokenStore.resolve(t), "还有令牌活着：" + t);
            }
        } finally {
            tokenStore.revokeAll(MEMBER_ID);
        }
    }

    @Test
    @DisplayName("乱七八糟的令牌一律返回 null，不抛异常")
    void 无效令牌不抛异常() {
        assertNull(tokenStore.resolve(null));
        assertNull(tokenStore.resolve(""));
        assertNull(tokenStore.resolve("随便写的"));
        // 前缀对但内容是伪造的
        assertNull(tokenStore.resolve("mb_" + "x".repeat(43)));
        // 管理端的 sa-token 令牌形态
        assertNull(tokenStore.resolve("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
    }

    private static String tokenKey(String tokenValue) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return "app:auth:t:" + java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
