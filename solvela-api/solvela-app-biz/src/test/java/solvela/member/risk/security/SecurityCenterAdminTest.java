package solvela.member.risk.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.base.module.config.ConfigService;
import solvela.biz.server.BizApplication;
import solvela.member.RateLimitRule;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.security.IpKeys;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 后台「安全中心」的几个服务（任务 T13）：限流规则的校验与保存、处罚查询与解除、蜜罐与关键字的增删停用、安全事件分页。
 * 连真库、真 Redis；改过的配置与规则在用例结束时还原。
 *
 * @Date 2026-10-17
 */
@SpringBootTest(classes = BizApplication.class)
class SecurityCenterAdminTest {

    @Autowired
    private RateLimitRuleAdminService rateLimitService;

    @Autowired
    private PenaltyAdminService penaltyService;

    @Autowired
    private SecurityGateAdminService gateService;

    @Autowired
    private SecurityIncidentAdminService incidentService;

    @Autowired
    private SecurityIncidentStore incidentStore;

    @Autowired
    private ConfigService configService;

    @Autowired
    private GatewaySnapshotPublisher snapshotPublisher;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String originalGate;

    private List<Map<String, Object>> originalRules;

    private String ip;

    @BeforeEach
    void setUp() {
        originalGate = configService.getConfigValue(ConfigKeyEnum.GATEWAY_SECURITY_GATE);
        originalRules = jdbcTemplate.queryForList("SELECT * FROM t_rate_limit_rule WHERE target_type = 1");
        ip = "198.51.100." + (1 + ThreadLocalRandom.current().nextInt(254));
    }

    @AfterEach
    void tearDown() {
        configService.updateValueByKey(ConfigKeyEnum.GATEWAY_SECURITY_GATE, originalGate);
        for (Map<String, Object> r : originalRules) {
            jdbcTemplate.update("""
                    UPDATE t_rate_limit_rule SET burst = ?, limit_3m = ?, limit_5m = ?, limit_10m = ?, long_window = ?,
                           limit_long = ?, mode = ? WHERE id = ?""",
                    r.get("burst"), r.get("limit_3m"), r.get("limit_5m"), r.get("limit_10m"), r.get("long_window"),
                    r.get("limit_long"), r.get("mode"), r.get("id"));
        }
        jdbcTemplate.update("DELETE FROM t_rate_limit_rule WHERE target_type = 2 AND description LIKE '%用例%'");
        jdbcTemplate.update("DELETE FROM t_security_incident WHERE client_ip LIKE '198.51.100.%'");
        redis.delete(List.of(SecurityKeys.securityPenaltyIp(IpKeys.ip(ip)),
                SecurityKeys.rateLimitPenalty("LOGIN", 1, IpKeys.ip(ip))));
        snapshotPublisher.publishQuietly("用例清理");
    }

    // ------------------------------------------------------------------ 限流规则

    @Test
    @DisplayName("🔴 保存前校验：突发 ≤ 3 ≤ 5 ≤ 10 分钟、三个窗口亚线性、长窗口要么都填要么都不填；现有种子全都过得了")
    void rateLimitValidation() {
        assertThrows(IllegalArgumentException.class, () -> values(20, 10, 15, 25).validate(),
                "突发比 3 分钟还宽");
        assertThrows(IllegalArgumentException.class, () -> values(5, 10, 20, 25).validate(),
                "5 分钟 ≥ 3 分钟的 5/3 倍：5 分钟窗口永远不会先触发");
        assertThrows(IllegalArgumentException.class, () -> new RateLimitRuleAdminService.RuleValues(5, 10, 15, 25, "1d", null, 2).validate());
        new RateLimitRuleAdminService.RuleValues(2, 3, 4, 5, "1d", 2, 2).validate();

        for (RateLimitRule r : rateLimitService.list()) {
            assertDoesNotThrow(() -> new RateLimitRuleAdminService.RuleValues(
                    r.getBurst(), r.getLimit3m(), r.getLimit5m(), r.getLimit10m(), r.getLongWindow(), r.getLimitLong(),
                    r.getMode()).validate(), "种子里的 " + r.getTarget() + " / " + r.getDimension() + " 自己都存不回去");
        }
    }

    @Test
    @DisplayName("改一行 → 库里变了、快照版本变了；清掉长窗口也存得进去；场景行不能删")
    void rateLimitUpdate() {
        RateLimitRule row = rateLimitService.list().stream()
                .filter(r -> r.getLongWindow() != null && r.getTargetType() == 1).findFirst().orElseThrow();
        String before = String.valueOf(redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_VERSION));

        rateLimitService.update(row.getId(), new RateLimitRuleAdminService.RuleValues(row.getBurst(), row.getLimit3m(),
                row.getLimit5m(), row.getLimit10m(), null, null, 1), "test");

        Map<String, Object> saved = jdbcTemplate.queryForMap("SELECT long_window, limit_long, mode FROM t_rate_limit_rule WHERE id = ?", row.getId());
        assertNull(saved.get("long_window"), "清掉长窗口要真的存进去（updateById 会跳过 null）");
        assertNull(saved.get("limit_long"));
        assertEquals(1, ((Number) saved.get("mode")).intValue());
        assertNotEquals(before, String.valueOf(redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_VERSION)),
                "保存即重发快照");
        assertThrows(IllegalArgumentException.class, () -> rateLimitService.deleteEndpoint(row.getId(), "test"));
    }

    @Test
    @DisplayName("接口覆盖只能用网关上报的路由；手填的接口键存不进去")
    void endpointMustBeReported() {
        assertThrows(IllegalArgumentException.class, () -> rateLimitService.addEndpoint("POST /no/such/route", 1,
                values(5, 10, 15, 25), "用例"));
    }

    // ------------------------------------------------------------------ 处罚

    @Test
    @DisplayName("按 IP 查到全局处罚与限流处罚；解除只认处罚键，不是「删任意 Redis 键」")
    void penaltyQueryAndRelease() {
        long until = Instant.now().getEpochSecond() + 600;
        String global = SecurityKeys.securityPenaltyIp(IpKeys.ip(ip));
        redis.opsForValue().set(global, String.valueOf(until), Duration.ofMinutes(10));
        String scene = SecurityKeys.rateLimitPenalty("LOGIN", 1, IpKeys.ip(ip));
        redis.opsForHash().put(scene, "until", String.valueOf(until));
        redis.opsForHash().put(scene, "level", "3");

        List<PenaltyAdminService.Penalty> found = penaltyService.query(ip, null, null);

        assertTrue(found.stream().anyMatch(p -> p.key().equals(global) && "GLOBAL".equals(p.kind()) && p.untilEpochSec() == until));
        assertTrue(found.stream().anyMatch(p -> p.key().equals(scene) && "LOGIN".equals(p.target()) && p.level() == 3));

        assertThrows(IllegalArgumentException.class, () -> penaltyService.release(SecurityKeys.RULE_SNAPSHOT, "test"),
                "解除接口不能拿来删规则快照");
        assertTrue(penaltyService.release(global, "test"));
        assertNull(redis.opsForValue().get(global));
    }

    // ------------------------------------------------------------------ 蜜罐与关键字

    @Test
    @DisplayName("🔴 蜜罐路径可增删停用；写宽了的关键字规则存不进去（同一份反例集）")
    void gateEdits() {
        gateService.addHoneypot("TEST_HP", "/test-honeypot", "test");
        assertTrue(gateService.get().honeypots().stream().anyMatch(h -> h.code().equals("TEST_HP") && h.enabled()));

        gateService.setEnabled(SecurityGateAdminService.Kind.HONEYPOT, "TEST_HP", false, "test");
        assertTrue(gateService.get().honeypots().stream().anyMatch(h -> h.code().equals("TEST_HP") && !h.enabled()));

        IllegalArgumentException wide = assertThrows(IllegalArgumentException.class,
                () -> gateService.addKeyword("OR_PAREN", "PARAM", "\\bor\\s*\\(", "test"));
        assertTrue(wide.getMessage().contains("Tea or (coffee)"), wide.getMessage());

        gateService.remove(SecurityGateAdminService.Kind.HONEYPOT, "TEST_HP", "test");
        assertTrue(gateService.get().honeypots().stream().noneMatch(h -> h.code().equals("TEST_HP")));
    }

    // ------------------------------------------------------------------ 安全事件

    @Test
    @DisplayName("安全事件按 IP 查得到；不带时间兜最近 7 天，超过 31 天拒绝")
    void incidentQuery() {
        incidentStore.store(SecurityIncidentEvent.builder(SecurityIncidentEvent.KEYWORD, "XSS_SCRIPT")
                .who(ip, null, null).request("POST", "/x", "<img src=x onerror=alert(1)>", "t").build());

        var page = incidentService.queryPage(new SecurityIncidentAdminService.Query(ip, null, null, null, null, null, 1, 20));

        assertEquals(1, page.total());
        assertEquals("<img src=x onerror=alert(1)>", page.list().get(0).userAgent(), "原样返回，转义是前端的事（只按纯文本渲染）");
        assertThrows(IllegalArgumentException.class, () -> incidentService.queryPage(new SecurityIncidentAdminService.Query(
                null, null, null, null, java.time.LocalDateTime.now().minusDays(40), null, 1, 20)));
    }

    private static RateLimitRuleAdminService.RuleValues values(int burst, int l3, int l5, int l10) {
        return new RateLimitRuleAdminService.RuleValues(burst, l3, l5, l10, null, null, 2);
    }
}
