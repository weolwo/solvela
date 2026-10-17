package solvela.app.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpKeys;
import solvela.security.SecurityKeys;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流判定：突发、窗口、阶梯、封顶、豁免、陪跑、接口覆盖、账号规范化。连真 Redis。
 *
 * <p>快照用测试专用的键；每个用例用随机的 IP / 设备号，计数键互不干扰（也不碰本机网关真在数的那些）。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class RateLimiterTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String KEY = "A".repeat(32);

    @Autowired
    private StringRedisTemplate redis;

    private String snapshotKey;

    private final List<String> touched = new ArrayList<>();

    @BeforeEach
    void setUp() {
        snapshotKey = "test:gw:rl:snapshot:" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        touched.add(snapshotKey);
        redis.delete(touched);
    }

    @Test
    @DisplayName("突发：第一次超只拒不罚；10 分钟内第二次超才判罚（第 1 档 60 秒）")
    void burstFirstRejectsThenPenalises() {
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.DEVICE, 2, 100, 100, 100)), Map.of());
        String device = device();

        limiter.check(login(null, device, null));
        limiter.check(login(null, device, null));
        ApiException first = assertThrows(ApiException.class, () -> limiter.check(login(null, device, null)));
        assertTrue(retryAfter(first) <= 10, "突发拒绝只该等到下一个令牌：" + retryAfter(first));

        ApiException second = assertThrows(ApiException.class, () -> limiter.check(login(null, device, null)));
        assertEquals(60, retryAfter(second), "第二次超突发 = 一次违规 = 第 1 档");
        assertEquals(ApiErrors.OPERATION_LIMITED, second.error());
    }

    @Test
    @DisplayName("窗口超了 → 判罚；处罚期间不计数、不延长")
    void windowViolationPenalisesAndDoesNotExtend() {
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.DEVICE, 100, 3, 100, 100)), Map.of());
        String device = device();
        for (int i = 0; i < 3; i++) {
            limiter.check(login(null, device, null));
        }
        assertEquals(60, retryAfter(assertThrows(ApiException.class, () -> limiter.check(login(null, device, null)))));

        String penaltyKey = SecurityKeys.rateLimitPenalty("LOGIN", RateLimiter.DEVICE, device);
        String until = (String) redis.opsForHash().get(penaltyKey, "until");
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> limiter.check(login(null, device, null)));
        }
        assertEquals(until, redis.opsForHash().get(penaltyKey, "until"), "处罚期间一直点不该延长处罚");
        assertEquals("1", redis.opsForHash().get(penaltyKey, "level"));
    }

    @Test
    @DisplayName("阶梯：处罚到期后再犯升一档（60 秒 → 5 分钟）")
    void ladderEscalates() {
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.DEVICE, 100, 1, 100, 100)), Map.of());
        String device = device();
        limiter.check(login(null, device, null));
        assertEquals(60, retryAfter(assertThrows(ApiException.class, () -> limiter.check(login(null, device, null)))));

        expirePenalty("LOGIN", RateLimiter.DEVICE, device);
        assertEquals(300, retryAfter(assertThrows(ApiException.class, () -> limiter.check(login(null, device, null)))),
                "到期之后马上再犯，应是第 2 档");
    }

    @Test
    @DisplayName("🔴 匿名场景的账号维度封顶 30 分钟：拿别人的邮箱打，最多让他等半小时")
    void anonymousAccountCapped() {
        String ladder = "{\"ladder\":[\"2h\"],\"anonAccountCap\":\"30m\"}";
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.ACCOUNT, 100, 1, 100, 100)),
                Map.of("gateway_rate_limit", ladder));
        String email = "cap-" + UUID.randomUUID() + "@example.com";
        limiter.check(login(email, null, null));
        assertEquals(1800, retryAfter(assertThrows(ApiException.class, () -> limiter.check(login(email, null, null)))));
    }

    @Test
    @DisplayName("账号按会员域的规则规范化：域名大小写不同、前后空格，都是同一个账号")
    void accountNormalised() {
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.ACCOUNT, 100, 1, 100, 100)), Map.of());
        String local = "norm-" + UUID.randomUUID();
        limiter.check(login(local + "@Example.COM", null, null));
        assertThrows(ApiException.class, () -> limiter.check(login("  " + local + "@example.com ", null, null)));
    }

    @Test
    @DisplayName("🔴 老设备豁免 IP 维度：共用出口后面的真人不被同出口的别人连累")
    void oldDeviceExemptFromIp() {
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.IP, 100, 1, 100, 100)), Map.of());
        String ip = ip();
        Instant old = Instant.now().minus(Duration.ofDays(8));
        for (int i = 0; i < 5; i++) {
            limiter.check(new RateLimiter.Request(RateLimitScene.LOGIN, null, ip, device(), old, null, null));
        }
        limiter.check(login(null, null, ip));
        assertThrows(ApiException.class, () -> limiter.check(login(null, null, ip)),
                "老设备的请求不计入 IP 维度；新设备照常数，第二次就超");
    }

    @Test
    @DisplayName("IP 白名单跳过 IP 维度；灰名单阈值减半")
    void whitelistAndGreylist() {
        String white = ip();
        String grey = ip();
        List<GatewayRuleSnapshot.IpEntry> lists = List.of(
                new GatewayRuleSnapshot.IpEntry(3, 1, white, null, 1, null),
                new GatewayRuleSnapshot.IpEntry(2, 1, grey, 30, 2, null));
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.IP, 100, 4, 100, 100)), Map.of(), lists);
        for (int i = 0; i < 10; i++) {
            limiter.check(login(null, null, white));
        }
        limiter.check(login(null, null, grey));
        limiter.check(login(null, null, grey));
        assertThrows(ApiException.class, () -> limiter.check(login(null, null, grey)), "灰名单：4 × 0.5 = 2，第 3 次就超");
    }

    @Test
    @DisplayName("陪跑：照常计数，但从不挡")
    void shadowNeverBlocks() {
        GatewayRuleSnapshot.Rule shadow = new GatewayRuleSnapshot.Rule(1, "LOGIN", RateLimiter.DEVICE,
                1, 1, 1, 1, null, null, 1);
        RateLimiter limiter = limiter(List.of(shadow), Map.of());
        String device = device();
        for (int i = 0; i < 10; i++) {
            limiter.check(login(null, device, null));
        }
    }

    @Test
    @DisplayName("接口覆盖：同一维度上接口行覆盖场景行，而且单独计数")
    void endpointOverride() {
        String endpoint = "POST /auth/login";
        GatewayRuleSnapshot.Rule override = new GatewayRuleSnapshot.Rule(2, endpoint, RateLimiter.DEVICE,
                100, 1, 100, 100, null, null, 2);
        RateLimiter limiter = limiter(List.of(rule("LOGIN", RateLimiter.DEVICE, 100, 50, 100, 100), override), Map.of());
        String device = device();
        RateLimiter.Request req = new RateLimiter.Request(RateLimitScene.LOGIN, endpoint, ip(), device, null, null, null);
        limiter.check(req);
        assertThrows(ApiException.class, () -> limiter.check(req), "接口行的阈值是 1，覆盖了场景行的 50");
        touched.add(SecurityKeys.rateLimitCounter(endpoint, RateLimiter.DEVICE, device));
        touched.add(SecurityKeys.rateLimitPenalty(endpoint, RateLimiter.DEVICE, device));
        assertNotNull(redis.opsForHash().get(SecurityKeys.rateLimitPenalty(endpoint, RateLimiter.DEVICE, device), "until"),
                "处罚落在接口行自己的键上");
    }

    @Test
    @DisplayName("IP 段只在匿名场景生效")
    void ipSegmentOnlyForAnonymousScenes() {
        String ip = ip();
        RateLimiter limiter = limiter(List.of(
                rule("REDEEM", RateLimiter.IP_SEGMENT, 100, 1, 100, 100),
                rule("LOGIN", RateLimiter.IP_SEGMENT, 100, 1, 100, 100)), Map.of());
        for (int i = 0; i < 3; i++) {
            limiter.check(new RateLimiter.Request(RateLimitScene.REDEEM, null, ip, null, null, 1L, null));
        }
        limiter.check(login(null, null, ip));
        assertThrows(ApiException.class, () -> limiter.check(login(null, null, ip)));
        touched.add(SecurityKeys.rateLimitCounter("LOGIN", RateLimiter.IP_SEGMENT, IpKeys.segment(ip)));
        touched.add(SecurityKeys.rateLimitPenalty("LOGIN", RateLimiter.IP_SEGMENT, IpKeys.segment(ip)));
    }

    // ------------------------------------------------------------------ 造数

    private RateLimiter limiter(List<GatewayRuleSnapshot.Rule> rules, Map<String, String> configs) {
        return limiter(rules, configs, List.of());
    }

    private RateLimiter limiter(List<GatewayRuleSnapshot.Rule> rules, Map<String, String> configs,
                                List<GatewayRuleSnapshot.IpEntry> lists) {
        GatewayRuleSnapshot snapshot = new GatewayRuleSnapshot("t-" + UUID.randomUUID(), 1L, rules, configs, lists);
        redis.opsForHash().putAll(snapshotKey, Map.of(
                SecurityKeys.SNAPSHOT_FIELD_VERSION, snapshot.version(),
                SecurityKeys.SNAPSHOT_FIELD_BUILT_AT, "1",
                SecurityKeys.SNAPSHOT_FIELD_BODY, MAPPER.writeValueAsString(snapshot)));
        String instance = "test-" + UUID.randomUUID();
        touched.add(SecurityKeys.loadedVersion(instance));
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, snapshotKey, instance);
        holder.refresh();
        return new RateLimiter(redis, holder, new RateLimitProperties(true, KEY));
    }

    private static GatewayRuleSnapshot.Rule rule(String scene, int dim, int burst, int l3, int l5, int l10) {
        return new GatewayRuleSnapshot.Rule(1, scene, dim, burst, l3, l5, l10, null, null, 2);
    }

    private RateLimiter.Request login(String account, String device, String ip) {
        return new RateLimiter.Request(RateLimitScene.LOGIN, "POST /auth/login", ip == null ? ip() : ip,
                device, device == null ? null : Instant.now(), null, account);
    }

    private String device() {
        String d = "test" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        for (String target : List.of("LOGIN")) {
            touched.add(SecurityKeys.rateLimitCounter(target, RateLimiter.DEVICE, d));
            touched.add(SecurityKeys.rateLimitPenalty(target, RateLimiter.DEVICE, d));
        }
        return d;
    }

    /** 198.18.0.0/15：留给测试的地址段（RFC 2544），不会撞上真实流量 */
    private String ip() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String ip = "198.18." + r.nextInt(256) + "." + (1 + r.nextInt(254));
        for (String target : List.of("LOGIN", "REDEEM")) {
            touched.add(SecurityKeys.rateLimitCounter(target, RateLimiter.IP, IpKeys.ip(ip)));
            touched.add(SecurityKeys.rateLimitPenalty(target, RateLimiter.IP, IpKeys.ip(ip)));
        }
        return ip;
    }

    private void expirePenalty(String target, int dim, String value) {
        String key = SecurityKeys.rateLimitPenalty(target, dim, value);
        redis.opsForHash().put(key, "until", String.valueOf(Instant.now().getEpochSecond() - 1));
    }

    private static long retryAfter(ApiException e) {
        return ((Number) e.details().get("retryAfterSeconds")).longValue();
    }
}
