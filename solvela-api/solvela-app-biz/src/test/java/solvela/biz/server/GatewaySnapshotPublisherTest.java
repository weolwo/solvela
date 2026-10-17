package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import solvela.base.json.JsonUtils;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.base.module.config.ConfigService;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListTypeEnum;
import solvela.member.risk.domain.SecurityListAddCmd;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.member.risk.service.MemberRiskAdminService;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.SecurityKeys;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关规则快照的发布方。连真库、真 Redis。
 *
 * <p>⚠️ 写的是本机真正的 {@code gw:rl:snapshot}（发布方不接受别的键）。每个用例结束都按库里的真值重新发布一次，
 * 本机网关不会因为跑了测试而拿着一份造出来的规则。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class GatewaySnapshotPublisherTest {

    private static final String DEFAULT_RESOURCE = "/solvela/security/default-gateway-snapshot.json";

    private static final String IP_PREFIX = "198.19.";

    @Autowired
    private GatewaySnapshotPublisher publisher;

    @Autowired
    private MemberRiskAdminService riskAdminService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restore() {
        jdbcTemplate.update("DELETE FROM t_security_list WHERE list_value LIKE ? AND reason LIKE '用例：%'", IP_PREFIX + "%");
        // 删掉再发：比较再写的保护下，被用例改大的 builtAt 会挡住之后所有真实发布
        redis.delete(SecurityKeys.RULE_SNAPSHOT);
        publisher.publish();
    }

    @Test
    @DisplayName("🔴 网关的内置默认值与库里的种子是同一套 —— 两份迟早只改了一份")
    void builtinDefaultsMatchSeed() throws Exception {
        GatewayRuleSnapshot fromDb = publisher.build();
        GatewayRuleSnapshot builtin;
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_RESOURCE)) {
            assertNotNull(in, "classpath 上没有 " + DEFAULT_RESOURCE);
            builtin = JsonUtils.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8), GatewayRuleSnapshot.class);
        }

        Comparator<GatewayRuleSnapshot.Rule> order = Comparator.comparing(GatewayRuleSnapshot.Rule::targetType)
                .thenComparing(GatewayRuleSnapshot.Rule::target)
                .thenComparing(GatewayRuleSnapshot.Rule::dimension);
        assertEquals(fromDb.rules().stream().sorted(order).toList(), builtin.rules().stream().sorted(order).toList(),
                "t_rate_limit_rule 与 solvela-contract 的 default-gateway-snapshot.json 不一致："
                        + "改了种子就要同步默认值（反过来也一样）");
        assertEquals(fromDb.configs(), builtin.configs(), "两条全局参数与内置默认值不一致");
    }

    @Test
    @DisplayName("发布 → Redis 里的版本、内容与读库拼出来的一致；内容不变时版本不变")
    void publishesSnapshot() {
        GatewaySnapshotPublisher.Outcome first = publisher.publish();
        GatewaySnapshotPublisher.Outcome second = publisher.publish();

        assertEquals(first.version(), second.version(), "内容没变，版本就不该变 —— 否则网关每 5 分钟白白重载一次");
        assertEquals(first.version(), redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_VERSION));
        GatewayRuleSnapshot body = JsonUtils.parseObject(
                (String) redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_BODY),
                GatewayRuleSnapshot.class);
        assertEquals(28, body.rules().size());
    }

    @Test
    @DisplayName("🔴 比较再写：Redis 里已经有更新的快照时，晚到的旧快照不许覆盖它")
    void olderSnapshotDoesNotOverwriteNewer() {
        publisher.publish();
        long future = System.currentTimeMillis() + 3_600_000L;
        redis.opsForHash().put(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_BUILT_AT, String.valueOf(future));
        redis.opsForHash().put(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_VERSION, "newer");

        GatewaySnapshotPublisher.Outcome outcome = publisher.publish();

        assertFalse(outcome.written());
        assertEquals("newer", redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_VERSION));
    }

    @Test
    @DisplayName("后台加一条 IP 黑名单 → 立刻发布，快照里有它；撤销 → 立刻没了")
    void listChangePublishesImmediately() {
        String ip = IP_PREFIX + (1 + ThreadLocalRandom.current().nextInt(250)) + "." + (1 + ThreadLocalRandom.current().nextInt(250));
        SecurityListAddCmd cmd = new SecurityListAddCmd();
        cmd.setListType(SecurityListTypeEnum.BLACK);
        cmd.setDimension(SecurityListDimensionEnum.IP);
        cmd.setListValue(ip);
        cmd.setReason("用例：快照发布");
        cmd.setOperator("用例");
        Long id = riskAdminService.addList(cmd).getId();

        assertTrue(snapshotIps().contains(ip), "加完名单没有立即发布，网关要等 5 分钟才知道");

        riskAdminService.revokeList(id, "用例", "用例：撤销");
        assertFalse(snapshotIps().contains(ip), "撤销之后快照里还有它");
    }

    @Test
    @DisplayName("后台改了网关的全局参数 → 立刻发布")
    void configChangePublishesImmediately() {
        String original = configService.getConfigValue(ConfigKeyEnum.GATEWAY_SECURITY_GATE);
        String changed = original.replace("\"distinctIps\":50", "\"distinctIps\":51");
        assertNotEquals(original, changed, "造数：种子里的熔断阈值应是 50");
        try {
            configService.updateValueByKey(ConfigKeyEnum.GATEWAY_SECURITY_GATE, changed);
            String inRedis = JsonUtils.parseObject(
                    (String) redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_BODY),
                    GatewayRuleSnapshot.class).configs().get(ConfigKeyEnum.GATEWAY_SECURITY_GATE.getValue());
            assertEquals(changed, inRedis);
        } finally {
            configService.updateValueByKey(ConfigKeyEnum.GATEWAY_SECURITY_GATE, original);
        }
    }

    private List<String> snapshotIps() {
        GatewayRuleSnapshot body = JsonUtils.parseObject(
                (String) redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_BODY),
                GatewayRuleSnapshot.class);
        return body.ipLists().stream().map(GatewayRuleSnapshot.IpEntry::value).toList();
    }
}
