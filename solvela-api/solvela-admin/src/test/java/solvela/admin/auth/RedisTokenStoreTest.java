package solvela.admin.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端令牌存储的行为，打真 Redis。
 *
 * <p>重点不是「签发能用」——那个一跑就知道。重点是四条<b>坏掉也不报错</b>的性质：
 * 令牌原文不落库、令牌一定带过期时间、吊销是真的吊销、挤号真的把旧会话挤掉了。
 * 这四条任意一条失效，系统都照常工作，只是安全性没了。
 */
@SpringBootTest
class RedisTokenStoreTest {

    private static final Long EMPLOYEE_ID = 999_000_001L;
    private static final String DEVICE = "PC";

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("签发的令牌能换回员工号，且带过期时间")
    void 签发与查询() {
        AccessToken token = tokenStore.issue(EMPLOYEE_ID, false, DEVICE);
        try {
            assertTrue(token.value().startsWith("ad_"), "令牌应带系统前缀，实际：" + token.value());

            TokenLookup lookup = tokenStore.lookup(token.value());
            AdminSession session = assertInstanceOf(TokenLookup.Authenticated.class, lookup).session();
            assertEquals(EMPLOYEE_ID, session.employeeId());
            assertEquals(DEVICE, session.device());

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
        AccessToken token = tokenStore.issue(EMPLOYEE_ID, false, DEVICE);
        try {
            // 原文当 key 查不到 —— 说明存的不是它
            assertNull(redis.opsForValue().get("admin:auth:t:" + token.value()));
            // 摘要当 key 查得到
            assertNotNull(redis.opsForValue().get(tokenKey(token.value())));

            // 反查集合里也只有摘要。dump 一次 Redis 就能登录任意后台账号，是这条断言在挡的事
            Set<String> digests = redis.opsForSet().members("admin:auth:e:" + EMPLOYEE_ID);
            assertNotNull(digests);
            assertTrue(digests.contains(digest(token.value())));
            assertTrue(digests.stream().noneMatch(d -> d.contains(token.value())));
        } finally {
            tokenStore.revoke(token.value());
        }
    }

    @Test
    @DisplayName("吊销之后立刻失效 —— 「后台点禁用，人当场用不了」靠的就是它")
    void 吊销立刻生效() {
        AccessToken token = tokenStore.issue(EMPLOYEE_ID, false, DEVICE);
        assertInstanceOf(TokenLookup.Authenticated.class, tokenStore.lookup(token.value()));

        tokenStore.revoke(token.value());
        assertInstanceOf(TokenLookup.Unknown.class, tokenStore.lookup(token.value()));
        assertNull(redis.opsForValue().get(tokenKey(token.value())));
    }

    @Test
    @DisplayName("🔴 revokeAll 吊销全部会话，万能密码登录的那条也不例外")
    void 按员工吊销全部() {
        AccessToken normal = tokenStore.issue(EMPLOYEE_ID, false, DEVICE);
        // singleSession 打开时上一条会被挤掉，所以这里只断言最后一条一定被吊销 ——
        // 而它恰恰是原实现漏掉的那种（万能密码会话的 loginId 格式不同，logout(loginId) 打不中）
        AccessToken superPwd = tokenStore.issue(EMPLOYEE_ID, true, DEVICE);

        tokenStore.revokeAll(EMPLOYEE_ID);

        assertInstanceOf(TokenLookup.Unknown.class, tokenStore.lookup(normal.value()));
        assertInstanceOf(TokenLookup.Unknown.class, tokenStore.lookup(superPwd.value()));
        assertTrue(Boolean.FALSE.equals(redis.hasKey("admin:auth:e:" + EMPLOYEE_ID)));
    }

    @Test
    @DisplayName("活跃超时：超过最低活跃频率没操作，令牌连同会话一起失效")
    void 活跃超时() throws InterruptedException {
        tokenStore.setActiveTimeoutSeconds(1);
        try {
            AccessToken token = tokenStore.issue(EMPLOYEE_ID, false, DEVICE);
            assertInstanceOf(TokenLookup.Authenticated.class, tokenStore.lookup(token.value()));

            TimeUnit.MILLISECONDS.sleep(1500);

            assertInstanceOf(TokenLookup.Inactive.class, tokenStore.lookup(token.value()),
                    "活跃期过了应判 Inactive —— 前端据此弹「长时间未操作」，与登录失效的处理不同");
            // 令牌本身也要被吊销：否则「重新登录」之后旧令牌仍然可用，等保那条要求就只是个弹窗
            assertInstanceOf(TokenLookup.Unknown.class, tokenStore.lookup(token.value()));
        } finally {
            tokenStore.setActiveTimeoutSeconds(-1);
        }
    }

    @Test
    @DisplayName("自定义有效期：万能密码会话必须短")
    void 自定义有效期() {
        AccessToken token = tokenStore.issue(EMPLOYEE_ID, true, DEVICE, Duration.ofMinutes(30));
        try {
            Long ttl = redis.getExpire(tokenKey(token.value()), TimeUnit.SECONDS);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= 1800,
                    "万能密码会话的 TTL 应当是 30 分钟量级，实际 " + ttl + " 秒。"
                            + "原实现写的是 180000000 秒（≈5.7 年），而注释说的是 30 分钟");
        } finally {
            tokenStore.revoke(token.value());
        }
    }

    private static String tokenKey(String token) {
        return "admin:auth:t:" + digest(token);
    }

    private static String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
