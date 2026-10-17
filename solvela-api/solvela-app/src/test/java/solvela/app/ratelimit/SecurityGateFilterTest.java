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
import solvela.app.auth.DeviceAuthProperties;
import solvela.auth.device.DeviceIdentity;
import solvela.auth.device.DeviceTokenCodec;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 安全闸 ①：IP / IP 段黑名单、全局处罚、设备封禁，以及老设备与白名单的豁免边界。连真 Redis。
 *
 * <p>判定用例直接调 {@link SecurityGateFilter#check}，快照放在测试专用的键上；
 * 最后两条走真的 HTTP 路径，验「被挡的请求在过滤器里结束、对外是同一个 429」。
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

    private final List<String> touched = new ArrayList<>();

    private final List<String> bannedDevices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        snapshotKey = "test:gw:rl:snapshot:" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        touched.add(snapshotKey);
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
    @DisplayName("自动黑名单碰上白名单 → 放行；人工黑名单才压得过白名单")
    void autoBlackLosesToWhite() {
        String ip = ip();
        SecurityGateFilter gate = gate(List.of(
                entry(IpLists.BLACK, ip, AUTO, null),
                entry(IpLists.WHITE, ip, MANUAL, null)));

        assertNull(gate.check(ip, null));
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
        // 快照里至少要有一条规则，否则 RuleSnapshotHolder 拒绝加载（那几乎一定是发布方读库出了问题）
        GatewayRuleSnapshot.Rule placeholder = new GatewayRuleSnapshot.Rule(1, "LOGIN", 1, 100, 100, 100, 100, null, null, 0);
        GatewayRuleSnapshot snapshot = new GatewayRuleSnapshot("t-" + UUID.randomUUID(), 1L, List.of(placeholder),
                Map.of(), lists);
        redis.opsForHash().putAll(snapshotKey, Map.of(
                SecurityKeys.SNAPSHOT_FIELD_VERSION, snapshot.version(),
                SecurityKeys.SNAPSHOT_FIELD_BUILT_AT, "1",
                SecurityKeys.SNAPSHOT_FIELD_BODY, MAPPER.writeValueAsString(snapshot)));
        String instance = "test-" + UUID.randomUUID();
        touched.add(SecurityKeys.loadedVersion(instance));
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, snapshotKey, instance);
        holder.refresh();
        return new SecurityGateFilter(redis, holder, null);
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
