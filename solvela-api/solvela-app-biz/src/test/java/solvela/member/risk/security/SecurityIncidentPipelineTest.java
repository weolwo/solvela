package solvela.member.risk.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import solvela.base.json.JsonUtils;
import solvela.base.module.config.ConfigService;
import solvela.base.module.redis.RedisService;
import solvela.biz.server.BizApplication;
import solvela.enums.DeviceStatusEnum;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListTypeEnum;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.device.DeviceDispositionService;
import solvela.member.device.DeviceService;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.member.risk.service.MemberRiskAdminService;
import solvela.security.IpKeys;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全事件从队列到库：落事件流水、按处置写名单（§6.3 的冲突规则）、设备观察档；名单维护任务的转灰、连坐、被挡次数；
 * 后台撤销连处罚键一起删。连真库、真 Redis。
 *
 * <p>队列与计数用测试专用的键（本机网关真在投的那个队列不碰）；IP 用 198.51.100.0/24（TEST-NET-2），结束时按前缀清掉。
 *
 * @Date 2026-10-17
 */
@SpringBootTest(classes = BizApplication.class)
class SecurityIncidentPipelineTest {

    private static final String PREFIX = "198.51.100.";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SecurityIncidentStore store;

    @Autowired
    private SecurityListWriter listWriter;

    @Autowired
    private RiskListCache listCache;

    @Autowired
    private GatewaySnapshotPublisher snapshotPublisher;

    @Autowired
    private ConfigService configService;

    @Autowired
    private MemberRiskAdminService adminService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceDispositionService dispositionService;

    @Autowired
    private RedisService redisService;

    private String ns;

    private int nextIp = ThreadLocalRandom.current().nextInt(254);

    private SecurityIncidentDrainJob drainJob;

    private final List<String> devices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ns = "test:" + UUID.randomUUID() + ":";
        drainJob = new SecurityIncidentDrainJob(redis, store, listCache, snapshotPublisher, ns + "queue", ns + "processing");
        cleanDb();
    }

    @AfterEach
    void tearDown() {
        redis.delete(redis.keys(ns + "*"));
        cleanDb();
        for (String d : devices) {
            jdbcTemplate.update("DELETE FROM t_device WHERE device_id = ?", d);
            redisService.delete(redisService.generateRedisKey("dev:observe:", d));
            redisService.delete(redisService.generateRedisKey("dev:active:", d));
        }
        // 用例造的名单进过快照：按库里的真值重发一次，本机网关不留着它们
        listCache.reload();
        snapshotPublisher.publishQuietly("用例清理");
    }

    private void cleanDb() {
        jdbcTemplate.update("DELETE FROM t_security_list WHERE list_value LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_security_incident WHERE client_ip LIKE ?", PREFIX + "%");
    }

    // ------------------------------------------------------------------ 落库与名单

    @Test
    @DisplayName("🔴 蜜罐事件 → 一行事件流水 + 一条自动黑名单（到期 = 处罚到期，证据指向那行事件）；再犯 → 命中次数 +1、到期取更晚的")
    void honeypotBlacklists() {
        String ip = ip();
        long until = now() + 86400;
        push(honeypot(ip, until));

        SecurityIncidentDrainJob.Result result = drainJob.drain();

        assertEquals(1, result.stored());
        assertTrue(result.listChanged());
        Map<String, Object> incident = jdbcTemplate.queryForMap(
                "SELECT id, incident_type, rule_code, action, UNIX_TIMESTAMP(penalty_until) AS pu FROM t_security_incident WHERE client_ip = ?", ip);
        assertEquals(1, ((Number) incident.get("incident_type")).intValue());
        assertEquals("ENV", incident.get("rule_code"));
        assertEquals(until, ((Number) incident.get("pu")).longValue());
        Map<String, Object> row = listRow(ip, 1);
        assertEquals(2, ((Number) row.get("source")).intValue());
        assertEquals("HONEYPOT", row.get("source_rule"));
        assertEquals(1, ((Number) row.get("hit_count")).intValue());
        assertEquals(until, ((Number) row.get("exp")).longValue());
        assertEquals(((Number) incident.get("id")).longValue(), ((Number) row.get("last_incident_id")).longValue());

        push(honeypot(ip, until + 3600));
        drainJob.drain();
        row = listRow(ip, 1);
        assertEquals(2, ((Number) row.get("hit_count")).intValue());
        assertEquals(until + 3600, ((Number) row.get("exp")).longValue());
    }

    @Test
    @DisplayName("🔴 已有人工条目 → 不动它（人工的长期黑名单不会被自动的 24 小时缩短）；已撤销的 → 复用这一行、命中次数归 1")
    void manualKeptRevokedReused() {
        String manual = ip();
        jdbcTemplate.update("""
                INSERT INTO t_security_list (list_type, dimension, list_value, reason, source, status, expire_time, operator)
                VALUES (1, 1, ?, '用例：人工', 1, 1, NOW() + INTERVAL 300 DAY, 'admin')""", manual);
        String revoked = ip();
        jdbcTemplate.update("""
                INSERT INTO t_security_list (list_type, dimension, list_value, reason, source, source_rule, hit_count, status,
                    expire_time, operator, revoke_operator, revoke_reason, revoke_time)
                VALUES (1, 1, ?, '用例：已撤销', 2, 'KEYWORD', 5, 0, NOW(), 'system', 'admin', '误判', NOW())""", revoked);

        push(honeypot(manual, now() + 86400));
        push(honeypot(revoked, now() + 86400));
        drainJob.drain();

        Map<String, Object> m = listRow(manual, 1);
        assertEquals(1, ((Number) m.get("source")).intValue());
        assertTrue(((Number) m.get("exp")).longValue() > now() + 200 * 86400L);
        Map<String, Object> r = listRow(revoked, 1);
        assertEquals(1, ((Number) r.get("status")).intValue());
        assertEquals(1, ((Number) r.get("hit_count")).intValue());
        assertNull(r.get("revoke_operator"));
        assertEquals("HONEYPOT", r.get("source_rule"));
    }

    @Test
    @DisplayName("限流事件：拉灰的进灰名单（带分）；要观察设备的把设备推进观察档；仅记录的只有流水")
    void rateLimitGreyAndObserve() {
        String grey = ip();
        String device = registeredDevice();
        push(SecurityIncidentEvent.builder(SecurityIncidentEvent.RATE_LIMIT, "LOGIN").penalty(4, now() + 3600)
                .action(SecurityIncidentEvent.ACTION_GREY, now() + 7 * 86400L).who(grey, null, null).build());
        push(SecurityIncidentEvent.builder(SecurityIncidentEvent.RATE_LIMIT, "LOGIN").penalty(4, now() + 3600)
                .observeDevice(true).who(ip(), device, null).build());
        String recordOnly = ip();
        push(SecurityIncidentEvent.builder(SecurityIncidentEvent.RATE_LIMIT, "LOGIN").penalty(1, now() + 60)
                .who(recordOnly, null, null).build());

        assertEquals(3, drainJob.drain().stored());

        Map<String, Object> row = listRow(grey, 2);
        assertEquals(SecurityListWriter.AUTO_GREY_SCORE, ((Number) row.get("score")).intValue());
        assertEquals("RATE_LIMIT", row.get("source_rule"));
        assertEquals(DeviceStatusEnum.OBSERVE, dispositionService.currentStatus(device));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_security_list WHERE list_value = ?",
                Integer.class, recordOnly));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_security_incident WHERE client_ip = ?",
                Integer.class, recordOnly));
    }

    @Test
    @DisplayName("🔴 上一轮崩在半路、留在「处理中」的事件，下一轮先做完；解不开的坏事件丢掉，不堵后面的")
    void recoversLeftoversAndDropsGarbage() {
        String ip = ip();
        redis.opsForList().leftPush(ns + "processing", JsonUtils.toJson(honeypot(ip, now() + 86400)));
        redis.opsForList().leftPush(ns + "queue", "{not json");
        push(honeypot(ip(), now() + 86400));

        SecurityIncidentDrainJob.Result result = drainJob.drain();

        assertEquals(2, result.stored());
        assertEquals(1, result.recovered());
        assertEquals(1, result.dropped());
        assertEquals(0L, redis.opsForList().size(ns + "processing"), "处理完的都要从「处理中」删掉");
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_security_incident WHERE client_ip = ?",
                Integer.class, ip));
    }

    // ------------------------------------------------------------------ 名单维护

    @Test
    @DisplayName("🔴 到期的自动黑名单 → 转灰 30 天、黑的那行撤销；同段 3 个 IP 被自动拉黑 → 整段拉灰（只拉灰），再跑不重复 +1")
    void escalateAndSegment() {
        String expired = ip();
        jdbcTemplate.update("""
                INSERT INTO t_security_list (list_type, dimension, list_value, reason, source, source_rule, status,
                    last_hit_time, expire_time, operator)
                VALUES (1, 1, ?, '用例：到期', 2, 'HONEYPOT', 1, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 MINUTE, 'system')""", expired);
        for (int i = 0; i < 3; i++) {
            listWriter.upsert(new SecurityListWriter.Entry(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IP, ip(), "HONEYPOT", "用例：连坐", now() + 86400, null));
        }
        SecurityListMaintainJob job = maintainJob();

        SecurityListMaintainJob.Result first = job.run();

        assertTrue(first.escalated() >= 1);
        Map<String, Object> grey = listRow(expired, 2);
        assertEquals("ESCALATE", grey.get("source_rule"));
        long days = (((Number) grey.get("exp")).longValue() - now()) / 86400;
        assertTrue(days >= 29 && days <= 30, "转灰 30 天：" + days);
        assertEquals(0, ((Number) listRow(expired, 1).get("status")).intValue(), "黑的那一行撤销");
        Map<String, Object> segment = listRow(PREFIX + "0/24", 2);
        assertEquals("SEGMENT", segment.get("source_rule"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_security_list WHERE list_value = ? AND list_type = 1", Integer.class, PREFIX + "0/24"),
                "🔴 永远不自动拉黑整段");

        job.run();
        assertEquals(1, ((Number) listRow(PREFIX + "0/24", 2).get("hit_count")).intValue(), "连坐每轮都会重新推导，不该每轮 +1");
    }

    @Test
    @DisplayName("网关攒在 Redis 的被挡次数 → 加到那条黑名单的 blocked_count，计数清零")
    void syncsBlockedCount() {
        String ip = ip();
        listWriter.upsert(new SecurityListWriter.Entry(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IP, ip, "HONEYPOT", "用例：被挡", now() + 86400, null));
        String key = ns + "blocked:1:" + ip;
        redis.opsForValue().set(key, "42");

        maintainJob().run();

        assertEquals(42L, ((Number) listRow(ip, 1).get("blocked_count")).longValue());
        assertEquals("0", redis.opsForValue().get(key));
    }

    @Test
    @DisplayName("🔴 后台撤销一条 IP 黑名单 → 网关的处罚键一起删掉（否则后台显示已解封，人还要被挡到处罚到期）")
    void revokeClearsPenalty() {
        String ip = ip();
        listWriter.upsert(new SecurityListWriter.Entry(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IP, ip, "HONEYPOT", "用例：撤销", now() + 86400, null));
        String penalty = SecurityKeys.securityPenaltyIp(IpKeys.ip(ip));
        redis.opsForValue().set(penalty, String.valueOf(now() + 86400));
        long id = ((Number) listRow(ip, 1).get("id")).longValue();

        assertTrue(adminService.revokeList(id, "admin", "用例：误判"));

        assertNull(redis.opsForValue().get(penalty));
    }

    // ------------------------------------------------------------------ 造数

    private SecurityListMaintainJob maintainJob() {
        return new SecurityListMaintainJob(jdbcTemplate, redis, listWriter, configService, listCache, snapshotPublisher,
                ns + "blocked:");
    }

    private void push(SecurityIncidentEvent event) {
        redis.opsForList().leftPush(ns + "queue", JsonUtils.toJson(event));
    }

    private static SecurityIncidentEvent honeypot(String ip, long until) {
        return SecurityIncidentEvent.builder(SecurityIncidentEvent.HONEYPOT, "ENV")
                .action(SecurityIncidentEvent.ACTION_BLACK, until).penalty(null, until)
                .who(ip, null, null).request("GET", "/.env", "curl/8.5.0", "t-test")
                .matched(SecurityIncidentEvent.FIELD_PATH, null, "/.env").build();
    }

    private Map<String, Object> listRow(String value, int listType) {
        return jdbcTemplate.queryForMap("""
                SELECT id, source, source_rule, hit_count, status, score, last_incident_id, blocked_count, revoke_operator,
                       UNIX_TIMESTAMP(expire_time) AS exp
                  FROM t_security_list WHERE list_value = ? AND list_type = ?""", value, listType);
    }

    private String registeredDevice() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String id = deviceService.register(new DeviceRegisterCmd("H5", null, null, null,
                "198.18." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250)))).deviceId();
        devices.add(id);
        return id;
    }

    /** 顺序取，同一个用例里不会撞上同一个 IP（随机取的话，「同段 3 个不同 IP」偶尔只凑出 2 个） */
    private String ip() {
        return PREFIX + (1 + (nextIp++ % 254));
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
