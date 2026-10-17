package solvela.app.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import solvela.app.auth.DeviceAuthProperties;
import solvela.auth.device.DeviceIdentity;
import solvela.auth.device.DeviceTokenCodec;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpKeys;
import solvela.security.KeywordRules;
import solvela.security.SecurityIncidentEvent;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 安全闸：① IP / IP 段黑名单、全局处罚、设备封禁与老设备豁免的边界；② 蜜罐与它的三道保险。连真 Redis。
 *
 * <p>判定用例直接调 {@link SecurityGateFilter#check} / {@link SecurityGateFilter#trap}，快照与熔断计数放在测试专用的键上；
 * 几条走真的 HTTP 路径，验「被挡的请求在过滤器里结束、对外是同一个 429」与「蜜罐的响应就是 404」。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class SecurityGateFilterTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final int MANUAL = 1, AUTO = 2;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DeviceTokenCodec codec;

    private String snapshotKey;

    private String breakerKey;

    private final List<String> touched = new ArrayList<>();

    private final List<String> bannedDevices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        snapshotKey = "test:gw:rl:snapshot:" + UUID.randomUUID();
        breakerKey = null;
    }

    @AfterEach
    void tearDown() {
        touched.add(snapshotKey);
        if (breakerKey != null) {
            touched.addAll(redis.keys(breakerKey + "*"));
        }
        redis.delete(touched);
        if (!bannedDevices.isEmpty()) {
            redis.opsForSet().remove(SecurityKeys.BANNED_DEVICES, bannedDevices.toArray());
        }
    }

    // ------------------------------------------------------------------ 黑名单（快照，纯内存）

    @Test
    @DisplayName("🔴 人工黑名单：老设备、白名单都不豁免 —— 那是有人看过之后签的字；没有到期时间就不给倒计时")
    void manualBlackNotExempt() {
        String ip = ip();
        SecurityGateFilter gate = gate(List.of(
                entry(IpLists.BLACK, ip, MANUAL, null),
                entry(IpLists.WHITE, ip, MANUAL, null)));

        PenaltyCache.Blocked blocked = gate.check(ip, oldDevice(newDeviceId()));

        assertNotNull(blocked);
        assertEquals(0, blocked.untilEpochSeconds());
    }

    @Test
    @DisplayName("🔴 自动黑名单：新设备挡、老设备放 —— 共用出口后面的真人带着自己的老设备")
    void autoBlackExemptsOldDevice() {
        String ip = ip();
        long expireAt = Instant.now().plus(Duration.ofHours(2)).toEpochMilli();
        SecurityGateFilter gate = gate(List.of(entry(IpLists.BLACK, ip, AUTO, expireAt)));

        PenaltyCache.Blocked blocked = gate.check(ip, newDevice(newDeviceId()));
        assertNotNull(blocked);
        assertEquals(expireAt / 1000, blocked.untilEpochSeconds(), "有到期时间就按它给倒计时");
        assertNotNull(gate.check(ip, null), "没带设备令牌的请求不豁免");

        assertNull(gate.check(ip, oldDevice(newDeviceId())));
    }

    @Test
    @DisplayName("🔴 IP 白名单在安全闸不豁免任何东西 —— 白名单只豁免限流（§6.3）")
    void whiteDoesNotExemptGate() {
        String ip = ip();
        SecurityGateFilter gate = gate(List.of(
                entry(IpLists.BLACK, ip, AUTO, null),
                entry(IpLists.WHITE, ip, MANUAL, null)));
        assertNotNull(gate.check(ip, null), "自动黑名单照样挡");

        String other = ip();
        penalise(SecurityKeys.securityPenaltyIp(IpKeys.ip(other)), 600);
        assertNotNull(gate(List.of(entry(IpLists.WHITE, other, MANUAL, null))).check(other, null),
                "白名单 IP 碰了蜜罐，处罚照样生效");
    }

    @Test
    @DisplayName("IP 段黑名单按 CIDR 命中；过期条目不算（按网关自己的时钟，不等下一次快照）")
    void segmentAndExpiry() {
        String ip = ip();
        String segment = ip.substring(0, ip.lastIndexOf('.')) + ".0/24";
        String expired = ip();
        SecurityGateFilter gate = gate(List.of(
                entry(IpLists.BLACK, segment, MANUAL, null),
                entry(IpLists.BLACK, expired, MANUAL, Instant.now().minusSeconds(1).toEpochMilli())));

        assertNotNull(gate.check(ip, null));
        if (!sameSegment(expired, ip)) {
            assertNull(gate.check(expired, null), "过期的黑名单条目不该再挡");
        }
    }

    // ------------------------------------------------------------------ 全局处罚与设备封禁（Redis）

    @Test
    @DisplayName("IP 全局处罚（蜜罐、关键字写的）→ 挡并给倒计时；老设备豁免")
    void ipPenalty() {
        String ip = ip();
        long until = penalise(SecurityKeys.securityPenaltyIp(IpKeys.ip(ip)), 600);
        SecurityGateFilter gate = gate(List.of());

        assertEquals(until, gate.check(ip, newDevice(newDeviceId())).untilEpochSeconds());
        assertNull(gate.check(ip, oldDevice(newDeviceId())));
    }

    @Test
    @DisplayName("🔴 设备全局处罚：老设备也挡 —— 设备维度是它自己的账，不存在「共用出口」")
    void devicePenaltyNoExemption() {
        String device = newDeviceId();
        long until = penalise(SecurityKeys.securityPenaltyDevice(device), 600);

        assertEquals(until, gate(List.of()).check(ip(), oldDevice(device)).untilEpochSeconds());
    }

    @Test
    @DisplayName("🔴 设备在封禁镜像里 → 挡，没有到期时间；不在 → 放")
    void bannedDevice() {
        String device = ban(newDeviceId());
        SecurityGateFilter gate = gate(List.of());

        PenaltyCache.Blocked blocked = gate.check(ip(), oldDevice(device));
        assertNotNull(blocked);
        assertEquals(0, blocked.untilEpochSeconds());
        assertNull(gate.check(ip(), oldDevice(newDeviceId())));
    }

    @Test
    @DisplayName("判定过一次的处罚进进程内缓存：Redis 那边删掉了，几秒内照样挡（被封的 IP 每秒几千次，不该每次都去 Redis）")
    void positiveResultCached() {
        String ip = ip();
        String key = SecurityKeys.securityPenaltyIp(IpKeys.ip(ip));
        penalise(key, 600);
        SecurityGateFilter gate = gate(List.of());
        assertNotNull(gate.check(ip, null));

        redis.delete(key);

        assertNotNull(gate.check(ip, null));
        assertNull(gate(List.of()).check(ip, null), "另一个实例（没缓存）读 Redis，已经放行");
    }

    @Test
    @DisplayName("什么都没命中 → 放行")
    void nothingMatched() {
        assertNull(gate(List.of()).check(ip(), newDevice(newDeviceId())));
    }

    // ------------------------------------------------------------------ 蜜罐

    @Test
    @DisplayName("🔴 命中蜜罐 → IP 与设备各罚 24 小时，下一个请求就挡（本实例不用等去 Redis）")
    void honeypotPenalises() {
        String ip = ip();
        String device = newDeviceId();
        SecurityGateFilter gate = honeypotGate(50);

        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.trap("/.env", ip, newDevice(device), null, null));

        String ipKey = track(SecurityKeys.securityPenaltyIp(IpKeys.ip(ip)));
        String deviceKey = track(SecurityKeys.securityPenaltyDevice(device));
        long ttl = redis.getExpire(ipKey);
        assertTrue(ttl > 86000 && ttl <= 86400, "IP 处罚 24 小时：" + ttl);
        assertTrue(redis.getExpire(deviceKey) > 86000);
        assertNotNull(gate.check(ip, null));
        assertNotNull(gate(List.of()).check(ip(), oldDevice(device)), "设备那一份在别的 IP、别的实例上照样挡");
    }

    @Test
    @DisplayName("前缀蜜罐按路径模板匹配；停用的、不在清单里的路径都不算")
    void honeypotMatching() {
        SecurityGateFilter gate = honeypotGate(50);

        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.trap("/.git/config", trackedIp(), null, "none", null));
        assertEquals(SecurityGateFilter.Trap.MISS, gate.trap("/disabled", trackedIp(), null, null, null));
        assertEquals(SecurityGateFilter.Trap.MISS, gate.trap("/auth/login", trackedIp(), null, null, null));
    }

    @Test
    @DisplayName("🔴 第 1 道保险：跨站发起的命中只记录不处罚 —— 否则一个图片标签就能替攻击者封掉任何访客")
    void crossSiteNotPenalised() {
        SecurityGateFilter gate = honeypotGate(50);
        String a = trackedIp(), b = trackedIp(), c = trackedIp();

        assertEquals(SecurityGateFilter.Trap.CROSS_SITE, gate.trap("/.env", a, null, "cross-site", "https://evil.example"));
        assertEquals(SecurityGateFilter.Trap.CROSS_SITE, gate.trap("/.env", b, null, "same-site", null));
        assertEquals(SecurityGateFilter.Trap.CROSS_SITE, gate.trap("/.env", c, null, null, "https://evil.example"),
                "老浏览器不带 Sec-Fetch-Site，但带着外站 Origin");
        assertNull(redis.opsForValue().get(SecurityKeys.securityPenaltyIp(IpKeys.ip(a))));
        assertNull(gate.check(a, null));

        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.trap("/.env", trackedIp(), null, "same-origin", null));
        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.trap("/.env", trackedIp(), null, "none", null),
                "用户自己在地址栏敲的");
    }

    @Test
    @DisplayName("🔴 第 2 道保险：窗口内太多不同 IP 命中 → 熔断，停止处罚（更可能是我们自己的前端出了 bug）")
    void breakerStopsPenalties() {
        SecurityGateFilter gate = honeypotGate(2);

        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.trap("/.env", trackedIp(), null, null, null));
        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.trap("/.env", trackedIp(), null, null, null));
        String third = trackedIp();
        assertEquals(SecurityGateFilter.Trap.BREAKER_OPEN, gate.trap("/.env", third, null, null, null));
        assertNull(redis.opsForValue().get(SecurityKeys.securityPenaltyIp(IpKeys.ip(third))));
    }

    @Test
    @DisplayName("🔴 第 3 道保险：蜜罐与真实接口重叠 → 启动失败；运行中加载到的重叠条目不启用")
    void overlapWithRealRoutes() {
        SecurityGateConfig config = SecurityGateConfig.parse(gateJson(50, """
                {"code":"LOGIN","path":"/auth/**","enabled":true},
                {"code":"DRAW","path":"/activity/x/draw","enabled":true},
                {"code":"ENV","path":"/.env","enabled":true}"""));
        assertEquals(List.of("LOGIN", "DRAW"), config.overlapping(List.of("/auth/login", "/activity/{activityCode}/draw"))
                .stream().map(SecurityGateConfig.Honeypot::code).toList());

        RequestMappingHandlerMapping mapping = context.getBean("requestMappingHandlerMapping",
                RequestMappingHandlerMapping.class);
        String json = gateJson(50, """
                {"code":"LOGIN","path":"/auth/login","enabled":true}""");
        SecurityGateFilter withRoutes = new SecurityGateFilter(redis, holder(List.of(), json), null, mapping, breakerKey(), incidents(), null);
        assertThrows(IllegalStateException.class, withRoutes::verifyHoneypotsAtStartup);
        assertEquals(SecurityGateFilter.Trap.MISS, withRoutes.trap("/auth/login", trackedIp(), null, null, null),
                "真实登录接口被当成蜜罐的话，所有人一登录就被封 24 小时");
    }

    @Test
    @DisplayName("🔴 蜜罐的响应与任何一个不存在的路径逐字节相同（除了链路 id），下一个请求才开始 429")
    void honeypotLooksLikeNotFound() throws Exception {
        SecurityGateFilter gate = honeypotGate(50);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(solvela.app.auth.DeviceFilter.class), gate).build();
        String ip = trackedIp();

        MvcResult honeypot = mvc.perform(get("/.env").header("X-Forwarded-For", ip)).andReturn();
        MvcResult missing = mvc.perform(get("/no-such-path-" + UUID.randomUUID().toString().substring(0, 8))
                .header("X-Forwarded-For", trackedIp())).andReturn();

        assertEquals(missing.getResponse().getStatus(), honeypot.getResponse().getStatus());
        assertEquals(withoutTrace(missing.getResponse().getContentAsString()),
                withoutTrace(honeypot.getResponse().getContentAsString()));
        assertEquals(429, mvc.perform(get("/.env").header("X-Forwarded-For", ip)).andReturn().getResponse().getStatus());
    }

    // ------------------------------------------------------------------ 安全事件与被挡次数

    @Test
    @DisplayName("🔴 蜜罐罚了 → 投一条事件：拉黑到处罚到期那一刻；跨站 / 熔断也各投一条「仅记录」，写明是被哪道保险压下的")
    void honeypotPublishesIncidents() {
        SecurityGateFilter gate = honeypotGate(1);
        String ip = trackedIp();
        String device = newDeviceId();
        track(SecurityKeys.securityPenaltyDevice(device));

        gate.trap(new SecurityGateFilter.Probe("GET", "/.env", ip, newDevice(device), null, null, "curl/8.5.0", "t-1"));
        gate.trap("/.env", trackedIp(), null, "cross-site", null);
        gate.trap("/.env", trackedIp(), null, null, null);

        List<SecurityIncidentEvent> events = incidentsPublished();
        assertEquals(3, events.size());
        SecurityIncidentEvent penalised = events.get(0);
        assertEquals(SecurityIncidentEvent.HONEYPOT, penalised.incidentType());
        assertEquals("ENV", penalised.ruleCode());
        assertEquals(SecurityIncidentEvent.ACTION_BLACK, penalised.action());
        assertEquals(penalised.penaltyUntilEpochSec(), penalised.listUntilEpochSec(), "名单与处罚同一时刻到期");
        assertEquals(ip, penalised.clientIp());
        assertEquals(device, penalised.deviceId());
        assertEquals("curl/8.5.0", penalised.userAgent());
        assertEquals(SecurityIncidentEvent.FIELD_PATH, penalised.matchedField());
        assertEquals(SecurityIncidentEvent.SUPPRESS_CROSS_SITE, events.get(1).suppressReason());
        assertEquals(SecurityIncidentEvent.ACTION_RECORD, events.get(1).action());
        assertEquals(SecurityIncidentEvent.SUPPRESS_BREAKER, events.get(2).suppressReason());
    }

    @Test
    @DisplayName("🔴 老设备碰了蜜罐：只罚设备，IP 那一份压下（共用出口后面的真人不该因为别人被封一天），事件写明老设备豁免")
    void oldDeviceSparesIp() {
        SecurityGateFilter gate = honeypotGate(50);
        String ip = trackedIp();
        String device = newDeviceId();
        String deviceKey = track(SecurityKeys.securityPenaltyDevice(device));

        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.trap("/.env", ip, oldDevice(device), null, null));

        assertNull(redis.opsForValue().get(SecurityKeys.securityPenaltyIp(IpKeys.ip(ip))));
        assertNotNull(redis.opsForValue().get(deviceKey));
        assertNull(gate.check(ip, newDevice(newDeviceId())), "同一出口后面别的设备照常放行");
        SecurityIncidentEvent event = incidentsPublished().get(0);
        assertEquals(SecurityIncidentEvent.SUPPRESS_OLD_DEVICE, event.suppressReason());
        assertEquals(SecurityIncidentEvent.ACTION_RECORD, event.action());
    }

    @Test
    @DisplayName("关键字事件写明命中位置与参数名、命中片段")
    void keywordIncidentFields() {
        SecurityGateFilter gate = keywordGate(50);

        gate.keyword(new KeywordRules.Hit("SQL_TAUTOLOGY", "query.id", "' or 1=1"), trackedIp(), null, null, null);

        SecurityIncidentEvent event = incidentsPublished().get(0);
        assertEquals(SecurityIncidentEvent.KEYWORD, event.incidentType());
        assertEquals(SecurityIncidentEvent.FIELD_QUERY, event.matchedField());
        assertEquals("id", event.matchedParam());
        assertEquals("' or 1=1", event.matchedSample());
    }

    @Test
    @DisplayName("🔴 被挡的请求只在进程里攒数，定时一次 INCRBY —— 不投事件（每挡一次写一条，等于替攻击者把库打满）")
    void blockedRequestsCounted() throws Exception {
        String ip = trackedIp();
        penalise(SecurityKeys.securityPenaltyIp(IpKeys.ip(ip)), 600);
        BlockedCounter counter = new BlockedCounter(redis);
        SecurityGateFilter gate = new SecurityGateFilter(redis, holder(List.of(), null),
                context.getBean("handlerExceptionResolver", HandlerExceptionResolver.class), null, breakerKey(),
                incidents(), counter);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(gate).build();

        for (int i = 0; i < 5; i++) {
            assertEquals(429, mvc.perform(post("/device/register").header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus());
        }
        counter.flush();

        String key = track(SecurityKeys.blockedCounter(1, IpKeys.listValue(ip)));
        assertEquals("5", redis.opsForValue().get(key));
        assertTrue(incidentsPublished().isEmpty());
    }

    // ------------------------------------------------------------------ 关键字

    @Test
    @DisplayName("🔴 种子里的关键字规则全部过得了反例集，一条都没被丢掉")
    void seededKeywordsAllLoad() {
        String seeded = new RuleSnapshotHolder(redis, "test:none:" + UUID.randomUUID(), "x").current()
                .configs().get("gateway_security_gate");
        assertEquals(13, SecurityGateConfig.parse(seeded).keywords().size());
    }

    @Test
    @DisplayName("扫路径、查询串（解码一次）、JSON 请求体的值、UA；🔴 密码与验证码字段里写什么都不触发")
    void scanLocations() {
        List<KeywordRules.Rule> rules = SecurityGateConfig.parse(new RuleSnapshotHolder(redis,
                "test:none:" + UUID.randomUUID(), "x").current().configs().get("gateway_security_gate")).keywords();

        assertEquals("query.id", SecurityGateFilter.scan(rules, "/goods", "id=1%20union%20select%20null,2", null, null).location());
        assertEquals("path", SecurityGateFilter.scan(rules, "/file/..%2F..%2Fetc%2Fpasswd", null, null, null).location());
        assertEquals("body.nickname", SecurityGateFilter.scan(rules, "/member", null,
                bytes("{\"nickname\":\"x' or 1=1\"}"), null).location());
        assertEquals("body.items[1].name", SecurityGateFilter.scan(rules, "/order", null,
                bytes("{\"items\":[{\"name\":\"ok\"},{\"name\":\"<script>alert(1)</script>\"}]}"), null).location());
        assertEquals("ua", SecurityGateFilter.scan(rules, "/", null, null, "sqlmap/1.8#stable").location());

        assertNull(SecurityGateFilter.scan(rules, "/auth/login", "code=1%27%20or%201%3D1", bytes(
                "{\"identity\":\"a@b.com\",\"credential\":\"x' or 1=1\",\"newPassword\":\"sleep(5)\",\"code\":\"' or 1=1\"}"),
                "curl/8.5.0"), "密码与验证码字段不扫；curl 不是扫描器");
        assertNull(SecurityGateFilter.scan(rules, "/goods/search", "q=Floor(%E8%90%BD%E5%9C%B0%E7%81%AF)",
                bytes("{\"nickname\":\"Doctor(医生)\",\"address\":\"解放路100号(东门)\"}"), null));
    }

    @Test
    @DisplayName("🔴 写宽了的规则存不进去：裸的 or( 会命中「Tea or (coffee)」；会指数回溯的正则在乱按键盘的输入上超时")
    void counterexamplesRejectWideRules() {
        IllegalArgumentException wide = assertThrows(IllegalArgumentException.class,
                () -> KeywordRules.compile("OR_PAREN", "PARAM", "\\bor\\s*\\("));
        assertTrue(wide.getMessage().contains("Tea or (coffee)"), wide.getMessage());
        // 本 JDK 对 (a+)+$ 这类经典写法已有优化，换一个仍会指数回溯的
        IllegalArgumentException slow = assertThrows(IllegalArgumentException.class,
                () -> KeywordRules.compile("SLOW", "PARAM", "(.*a){25}x"));
        assertTrue(slow.getMessage().contains("太慢"), slow.getMessage());
        assertThrows(IllegalArgumentException.class, () -> KeywordRules.compile("CURL", "UA", "curl"),
                "curl 不是扫描器");
        assertThrows(IllegalArgumentException.class, () -> KeywordRules.compile("bad code", "PARAM", "x"));
    }

    @Test
    @DisplayName("关键字命中 → IP 与设备罚 24 小时；路径与查询串上的跨站命中拒绝但不罚；请求体上的不看跨站")
    void keywordPenalties() {
        SecurityGateFilter gate = keywordGate(50);
        String ip = trackedIp();
        String device = newDeviceId();
        track(SecurityKeys.securityPenaltyDevice(device));
        KeywordRules.Hit inQuery = new KeywordRules.Hit("SQL_TAUTOLOGY", "query.id", "' or 1=1");
        KeywordRules.Hit inBody = new KeywordRules.Hit("SQL_TAUTOLOGY", "body.nickname", "' or 1=1");

        String crossSite = trackedIp();
        assertEquals(SecurityGateFilter.Trap.CROSS_SITE, gate.keyword(inQuery, crossSite, null, "cross-site", null));
        assertNull(gate.check(crossSite, null));
        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.keyword(inBody, trackedIp(), null, "cross-site", null),
                "JSON 请求体从别的网站发不过来，Sec-Fetch-Site 说什么都不算数");

        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.keyword(inQuery, ip, newDevice(device), null, null));
        assertNotNull(gate.check(ip, null));
        assertNotNull(gate(List.of()).check(trackedIp(), oldDevice(device)));
    }

    @Test
    @DisplayName("🔴 每条规则各自熔断，熔断时放行（那时挡下的多半是真人）；别的规则不受连累")
    void keywordBreakerPerRule() {
        SecurityGateFilter gate = keywordGate(2);
        KeywordRules.Hit hit = new KeywordRules.Hit("SQL_TAUTOLOGY", "body.nickname", "' or 1=1");

        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.keyword(hit, trackedIp(), null, null, null));
        assertEquals(SecurityGateFilter.Trap.PENALISED, gate.keyword(hit, trackedIp(), null, null, null));
        assertEquals(SecurityGateFilter.Trap.BREAKER_OPEN, gate.keyword(hit, trackedIp(), null, null, null));
        assertEquals(SecurityGateFilter.Trap.PENALISED,
                gate.keyword(new KeywordRules.Hit("XSS_SCRIPT", "body.nickname", "<script"), trackedIp(), null, null, null));
    }

    @Test
    @DisplayName("🔴 关键字命中的回答与参数校验失败逐字相同（除了链路 id）；之后的请求 429；干净的请求体 Controller 照样读得到")
    void keywordLooksLikeBadRequest() throws Exception {
        SecurityGateFilter gate = keywordGate(50);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(solvela.app.auth.DeviceFilter.class), gate).build();
        String ip = trackedIp();

        MvcResult probe = mvc.perform(post("/auth/login").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"a@b.com' or 1=1 -- \",\"credential\":\"x\"}"))
                .andReturn();
        MvcResult malformed = mvc.perform(post("/auth/login").header("X-Forwarded-For", trackedIp())
                .contentType(MediaType.APPLICATION_JSON).content("{")).andReturn();

        assertEquals(400, probe.getResponse().getStatus());
        assertEquals(withoutTrace(malformed.getResponse().getContentAsString()),
                withoutTrace(probe.getResponse().getContentAsString()));
        assertEquals(429, mvc.perform(post("/auth/login").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus());

        MvcResult clean = mvc.perform(post("/auth/login").header("X-Forwarded-For", trackedIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"floor@example.com\",\"credential\":\"Color(2024)!\"}"))
                .andReturn();
        // 本用例没有桩下游，绑定成功的请求会走到「调 app-biz」那一步（503）；请求体丢了的话是参数校验的 400
        assertNotEquals(400, clean.getResponse().getStatus(),
                "请求体被安全闸读过之后要原样交给 Controller：" + clean.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ 真的 HTTP 路径

    @Test
    @DisplayName("🔴 被罚的 IP 打一个不限流的接口 → 照样在安全闸 429 + Retry-After（挡所有接口，不只登录）")
    void httpIpPenalty() throws Exception {
        String ip = ip();
        penalise(SecurityKeys.securityPenaltyIp(IpKeys.ip(ip)), 600);

        MvcResult result = mvc().perform(post("/device/register").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        assertEquals(429, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("OPERATION_LIMITED"),
                "和限流同一句话：不告诉对方是哪一道闸");
        long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter > 500 && retryAfter <= 600, "Retry-After = 处罚剩余秒数：" + retryAfter);
    }

    @Test
    @DisplayName("🔴 被封的设备带着令牌来 → 429，不带倒计时（封禁没有到期时间）")
    void httpBannedDevice() throws Exception {
        String device = ban(newDeviceId());

        MvcResult result = mvc().perform(post("/device/register").header("X-Forwarded-For", ip())
                .header(DeviceAuthProperties.DEFAULT_HEADER, codec.issue(device, "APP"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        assertEquals(429, result.getResponse().getStatus());
        assertNull(result.getResponse().getHeader("Retry-After"));
    }

    // ------------------------------------------------------------------ 造数

    private SecurityGateFilter gate(List<GatewayRuleSnapshot.IpEntry> lists) {
        return new SecurityGateFilter(redis, holder(lists, null), null, null, breakerKey(), incidents(), null);
    }

    /** 一份带蜜罐的配置：/.env、/.git/**，外加一条停用的 /disabled */
    private SecurityGateFilter honeypotGate(int breakerDistinctIps) {
        String json = gateJson(breakerDistinctIps, """
                {"code":"ENV","path":"/.env","enabled":true},
                {"code":"GIT","path":"/.git/**","enabled":true},
                {"code":"OFF","path":"/disabled","enabled":false}""");
        return new SecurityGateFilter(redis, holder(List.of(), json),
                context.getBean("handlerExceptionResolver", HandlerExceptionResolver.class), null, breakerKey(), incidents(), null);
    }

    private static String gateJson(int breakerDistinctIps, String honeypots) {
        return gateJson(breakerDistinctIps, honeypots, "");
    }

    private static String gateJson(int breakerDistinctIps, String honeypots, String keywords) {
        return "{\"ban\":\"24h\",\"breaker\":{\"window\":\"10m\",\"distinctIps\":" + breakerDistinctIps
                + "},\"honeypotPaths\":[" + honeypots + "],\"keywords\":[" + keywords + "]}";
    }

    /** 带关键字规则的配置：取内置默认值里的那一份（与种子相同），熔断阈值换成用例给的 */
    private SecurityGateFilter keywordGate(int breakerDistinctIps) {
        String seeded = new RuleSnapshotHolder(redis, "test:none:" + UUID.randomUUID(), "x").current()
                .configs().get("gateway_security_gate");
        String json = seeded.replace("\"distinctIps\":50", "\"distinctIps\":" + breakerDistinctIps);
        return new SecurityGateFilter(redis, holder(List.of(), json),
                context.getBean("handlerExceptionResolver", HandlerExceptionResolver.class), null, breakerKey(), incidents(), null);
    }

    private RuleSnapshotHolder holder(List<GatewayRuleSnapshot.IpEntry> lists, String gateConfig) {
        // 快照里至少要有一条规则，否则 RuleSnapshotHolder 拒绝加载（那几乎一定是发布方读库出了问题）
        GatewayRuleSnapshot.Rule placeholder = new GatewayRuleSnapshot.Rule(1, "LOGIN", 1, 100, 100, 100, 100, null, null, 0);
        GatewayRuleSnapshot snapshot = new GatewayRuleSnapshot("t-" + UUID.randomUUID(), 1L, List.of(placeholder),
                gateConfig == null ? Map.of() : Map.of("gateway_security_gate", gateConfig), lists);
        redis.opsForHash().putAll(snapshotKey, Map.of(
                SecurityKeys.SNAPSHOT_FIELD_VERSION, snapshot.version(),
                SecurityKeys.SNAPSHOT_FIELD_BUILT_AT, "1",
                SecurityKeys.SNAPSHOT_FIELD_BODY, MAPPER.writeValueAsString(snapshot)));
        String instance = "test-" + UUID.randomUUID();
        touched.add(SecurityKeys.loadedVersion(instance));
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, snapshotKey, instance);
        holder.refresh();
        return holder;
    }

    /** 事件投进这个用例自己的队列（在熔断前缀下面，用例结束一起删） */
    private IncidentPublisher incidents() {
        return new IncidentPublisher(redis, breakerKey() + "incident");
    }

    /** 熔断键的前缀：每个用例一个，用例结束时把这个前缀下的键一起删掉 */
    private String breakerKey() {
        if (breakerKey == null) {
            breakerKey = "test:" + UUID.randomUUID() + ":";
        }
        return breakerKey;
    }

    private String track(String key) {
        touched.add(key);
        return key;
    }

    /** 一个随机 IP，并登记它的处罚键（蜜罐用例会写下它） */
    private String trackedIp() {
        String ip = ip();
        track(SecurityKeys.securityPenaltyIp(IpKeys.ip(ip)));
        return ip;
    }

    private List<SecurityIncidentEvent> incidentsPublished() {
        List<String> raw = redis.opsForList().range(breakerKey() + "incident", 0, -1);
        List<SecurityIncidentEvent> events = new ArrayList<>();
        // LPUSH 进去的：最新的在最前面，倒过来按发生顺序
        for (int i = raw.size() - 1; i >= 0; i--) {
            events.add(MAPPER.readValue(raw.get(i), SecurityIncidentEvent.class));
        }
        return events;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String withoutTrace(String body) {
        return body.replaceAll("\"traceId\"\\s*:\\s*\"[^\"]*\"", "\"traceId\":\"-\"");
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(
                context.getBean(solvela.app.auth.DeviceFilter.class),
                context.getBean(SecurityGateFilter.class)).build();
    }

    private static GatewayRuleSnapshot.IpEntry entry(int type, String value, int source, Long expireAt) {
        return new GatewayRuleSnapshot.IpEntry(type, value.contains("/") ? 2 : 1, value, null, source, expireAt);
    }

    /** 写一个全局处罚键，返回到期（epoch 秒） */
    private long penalise(String key, long seconds) {
        long until = Instant.now().getEpochSecond() + seconds;
        redis.opsForValue().set(key, String.valueOf(until), Duration.ofSeconds(seconds));
        touched.add(key);
        return until;
    }

    private String ban(String device) {
        redis.opsForSet().add(SecurityKeys.BANNED_DEVICES, device);
        bannedDevices.add(device);
        return device;
    }

    private static String newDeviceId() {
        return DeviceTokenCodec.newDeviceId();
    }

    /** 签发于 30 天前：过了默认的 7 天，算老设备 */
    private static DeviceIdentity oldDevice(String id) {
        return new DeviceIdentity(id, "APP", 1, Instant.now().minus(Duration.ofDays(30)));
    }

    private static DeviceIdentity newDevice(String id) {
        return new DeviceIdentity(id, "APP", 1, Instant.now());
    }

    private static boolean sameSegment(String a, String b) {
        return a.substring(0, a.lastIndexOf('.')).equals(b.substring(0, b.lastIndexOf('.')));
    }

    /** 198.18.0.0/15：留给测试的地址段（RFC 2544），不会撞上真实流量 */
    private static String ip() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.18." + r.nextInt(256) + "." + (1 + r.nextInt(254));
    }
}
