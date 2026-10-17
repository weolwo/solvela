package solvela.app.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.auth.member.MemberAccessToken;
import solvela.auth.member.MemberSession;
import solvela.auth.member.MemberSessionContext;
import solvela.auth.member.MemberTokenStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「我的登录设备」：列出活着的会话，并能把某一个踢下线。
 *
 * <h3>这套用例里最要紧的是【旧令牌兼容】那一条</h3>
 * 2026-09-10 之前，Redis 里那条会话记录是<b>光秃秃一个 memberId</b>；
 * 现在是七个字段拼起来的。而线上此刻正躺着一批旧格式的令牌，有效期 30 天。
 *
 * <p>解析如果只认新格式，<b>发版当天所有已登录用户全部掉线</b> ——
 * 那不是一个会报错的 bug，是几万个人同时被登出，而服务端日志里一切正常。
 *
 * <h3>其余三条也都是「坏了不报错」</h3>
 * <ul>
 *   <li><b>当前会话要标出来</b> —— 不标的话用户会把自己这台点下线；</li>
 *   <li><b>只能踢自己的</b> —— sessionId 出现在响应体里，不是秘密，
 *       服务端拿它全局去删的话，任何人都能踢别人下线；</li>
 *   <li><b>踢下线要真的失效</b> —— 列表里少了一行但令牌还能用，是最糟的那种：
 *       用户以为自己处理完了。</li>
 * </ul>
 *
 * @Date 2026-09-10
 */
@SpringBootTest
class MemberSessionListTest {

    private static final Long MEMBER_ID = 999_000_003L;

    @Autowired
    private MemberTokenStore tokenStore;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        tokenStore.revokeAll(MEMBER_ID);
        tokenStore.revokeAll(MEMBER_ID + 1);
    }

    private MemberAccessToken login(String deviceType, String deviceId, String ip) {
        return tokenStore.issue(MEMBER_ID, new MemberSessionContext(deviceType, deviceId, ip, null));
    }

    private static String tokenKey(String tokenValue) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return "app:auth:t:" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================== 列表 ==============================

    @Test
    @DisplayName("列出活着的会话，并带上登录时那点上下文")
    void 列出会话() {
        MemberAccessToken app = login("APP", "0123456789abcdef0123456789abcdef", "203.0.113.5");
        login("H5", null, "198.51.100.9");

        List<MemberSession> sessions = tokenStore.listSessions(MEMBER_ID, app.value());

        assertEquals(2, sessions.size());
        MemberSession current = sessions.get(0);
        assertTrue(current.current(), "当前这一台必须排最前 —— 找不到自己的话，用户不敢点任何下线按钮");
        assertEquals("APP", current.deviceType());
        assertEquals("203.0.113.5", current.ip());
        assertEquals("0123456789abcdef0123456789abcdef", current.deviceId());
        assertTrue(current.loginTime() > 0, "登录时间要有 —— 「这是什么时候登的」是用户判断的主要依据");
    }

    @Test
    @DisplayName("🔴 只有一条 current=true，而且就是当前令牌那一条")
    void 只标出当前一条() {
        login("H5", null, "198.51.100.1");
        MemberAccessToken mine = login("APP", null, "203.0.113.1");

        List<MemberSession> sessions = tokenStore.listSessions(MEMBER_ID, mine.value());

        assertEquals(1, sessions.stream().filter(MemberSession::current).count());
        assertEquals("APP", sessions.get(0).deviceType());
    }

    @Test
    @DisplayName("不传当前令牌时，一条都不标 —— 而不是随便标一条")
    void 不知道当前是哪条() {
        login("H5", null, "198.51.100.1");

        assertTrue(tokenStore.listSessions(MEMBER_ID, null).stream().noneMatch(MemberSession::current));
    }

    @Test
    @DisplayName("令牌自然过期后不再出现在列表里，集合里的残留也被清掉")
    void 过期的不再列出() {
        MemberAccessToken alive = login("APP", null, "203.0.113.1");
        MemberAccessToken dying = login("H5", null, "198.51.100.1");
        // 让其中一个立刻过期
        redis.expire(tokenKey(dying.value()), Duration.ofMillis(1));
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<MemberSession> sessions = tokenStore.listSessions(MEMBER_ID, alive.value());

        assertEquals(1, sessions.size(), "过期的会话还列出来的话，用户会对着一个点不动的下线按钮发愁");
        assertEquals(1, redis.opsForSet().size("app:auth:m:" + MEMBER_ID),
                "集合里的残留要顺手清掉，否则它只增不减");
    }

    // ============================== 下线 ==============================

    @Test
    @DisplayName("🔴 踢下线之后那个令牌立刻失效 —— 列表少一行但令牌还能用是最糟的")
    void 下线是真的下线() {
        MemberAccessToken mine = login("APP", null, "203.0.113.1");
        MemberAccessToken other = login("H5", null, "198.51.100.1");
        String otherSessionId = tokenStore.listSessions(MEMBER_ID, mine.value()).stream()
                .filter(s -> !s.current()).findFirst().orElseThrow().sessionId();

        assertTrue(tokenStore.revokeSession(MEMBER_ID, otherSessionId));

        assertNull(tokenStore.resolve(other.value()), "用户以为自己处理完了，实际那台还登着");
        assertNotNull(tokenStore.resolve(mine.value()), "不该误伤自己这一个");
    }

    @Test
    @DisplayName("🔴 踢不了别人的会话 —— sessionId 会出现在响应体里，不是秘密")
    void 只能踢自己的() {
        MemberAccessToken victim = tokenStore.issue(MEMBER_ID + 1, MemberSessionContext.empty());
        String victimSessionId = tokenStore.listSessions(MEMBER_ID + 1, null).get(0).sessionId();
        login("APP", null, "203.0.113.1");

        assertFalse(tokenStore.revokeSession(MEMBER_ID, victimSessionId),
                "拿别人的 sessionId 应该什么都踢不掉");
        assertNotNull(tokenStore.resolve(victim.value()), "别人的会话必须原封不动");
    }

    @Test
    @DisplayName("下线其它设备：自己留着，其余全掉")
    void 下线其它设备() {
        MemberAccessToken mine = login("APP", null, "203.0.113.1");
        MemberAccessToken a = login("H5", null, "198.51.100.1");
        MemberAccessToken b = login("PC", null, "198.51.100.2");

        assertEquals(2, tokenStore.revokeOthers(MEMBER_ID, mine.value()));

        assertNotNull(tokenStore.resolve(mine.value()),
                "自己也被踢掉的话，用户会犹豫要不要点 —— 而犹豫的那几分钟里别人还登着");
        assertNull(tokenStore.resolve(a.value()));
        assertNull(tokenStore.resolve(b.value()));
    }

    @Test
    @DisplayName("下线一个已经不在的会话 → false，不抛异常")
    void 下线不存在的会话() {
        login("APP", null, "203.0.113.1");

        assertFalse(tokenStore.revokeSession(MEMBER_ID, "not-a-real-session"));
    }

    // ============================== 旧令牌兼容 ==============================

    @Test
    @DisplayName("🔴 旧格式令牌照常能用 —— 只认新格式的话，发版当天全员掉线")
    void 旧令牌还能解析() {
        MemberAccessToken token = login("APP", null, "203.0.113.1");
        // 把值改回 2026-09-10 之前的样子：光秃秃一个 memberId
        redis.opsForValue().set(tokenKey(token.value()), String.valueOf(MEMBER_ID), Duration.ofMinutes(5));

        assertEquals(MEMBER_ID, tokenStore.resolve(token.value()),
                "令牌有效期 30 天，线上此刻正躺着一批旧格式的。认不出来 = 几万人同时被登出");
    }

    @Test
    @DisplayName("🔴 旧格式令牌也要【列出来】，只是信息不全 —— 消失比信息不全更吓人")
    void 旧令牌也列出来() {
        MemberAccessToken token = login("APP", null, "203.0.113.1");
        redis.opsForValue().set(tokenKey(token.value()), String.valueOf(MEMBER_ID), Duration.ofMinutes(5));

        List<MemberSession> sessions = tokenStore.listSessions(MEMBER_ID, token.value());

        assertEquals(1, sessions.size(), "让它消失的话，用户会以为「有个设备我看不见」");
        assertTrue(sessions.get(0).current());
        assertNull(sessions.get(0).deviceType(), "旧记录里本来就没有这些，如实留空");
        assertNotNull(sessions.get(0).sessionId(), "仍然要给一个 sessionId，否则这一行下线不了");
    }

    @Test
    @DisplayName("旧格式令牌也能被踢下线")
    void 旧令牌能下线() {
        MemberAccessToken mine = login("APP", null, "203.0.113.1");
        MemberAccessToken legacy = login("H5", null, "198.51.100.1");
        redis.opsForValue().set(tokenKey(legacy.value()), String.valueOf(MEMBER_ID), Duration.ofMinutes(5));
        String legacySessionId = tokenStore.listSessions(MEMBER_ID, mine.value()).stream()
                .filter(s -> !s.current()).findFirst().orElseThrow().sessionId();

        assertTrue(tokenStore.revokeSession(MEMBER_ID, legacySessionId));

        assertNull(tokenStore.resolve(legacy.value()));
    }
}
