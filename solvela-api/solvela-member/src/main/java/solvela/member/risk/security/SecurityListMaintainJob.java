package solvela.member.risk.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.base.module.config.ConfigService;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListTypeEnum;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.security.IpKeys;
import solvela.security.SecurityKeys;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每 5 分钟维护一次安全名单（网关方案 §5.5「什么事件进什么名单」、§6.3）：
 * <ol>
 *   <li><b>黑到期转灰</b>：到期的自动黑名单 → 同一个值进灰名单 {@code escalateGreyDays} 天（{@code ESCALATE}），黑的那一行撤销。
 *       封禁结束后它还背着前科 —— 「以后只要是记录里的 IP 就提高风控」；</li>
 *   <li><b>同段连坐</b>：{@code segmentGrey.within} 内同一个 /24（IPv6 /48）里有 {@code minIps} 个以上 IP 被自动拉黑 →
 *       整段拉灰 {@code days} 天（{@code SEGMENT}）。🔴 <b>只拉灰，永远不自动拉黑整段</b>；</li>
 *   <li><b>被挡次数</b>：网关攒在 Redis 里的计数（{@code sec:blocked:*}）加到对应黑名单的 {@code blocked_count}。</li>
 * </ol>
 * 名单变了就重发网关快照。参数取 {@code t_config.gateway_security_gate}，解不开的用默认值。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@SolvelaJobHandler(
        name = "securityListMaintain",
        title = "【安全】安全名单维护",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 30
)
public class SecurityListMaintainJob implements SolvelaJob {

    /** 一轮最多转这么多条：积压很多时分几轮做完，不让一轮超时 */
    static final int ESCALATE_BATCH = 1000;

    private final JdbcTemplate jdbcTemplate;

    private final StringRedisTemplate redis;

    private final SecurityListWriter listWriter;

    private final ConfigService configService;

    private final RiskListCache listCache;

    private final GatewaySnapshotPublisher snapshotPublisher;

    /** 被挡计数的键前缀：生产就是 {@link SecurityKeys#BLOCKED_COUNTER_PREFIX}，测试换一个 */
    private final String blockedPrefix;

    @Autowired
    public SecurityListMaintainJob(JdbcTemplate jdbcTemplate, StringRedisTemplate redis, SecurityListWriter listWriter,
                                   ConfigService configService, RiskListCache listCache,
                                   GatewaySnapshotPublisher snapshotPublisher) {
        this(jdbcTemplate, redis, listWriter, configService, listCache, snapshotPublisher,
                SecurityKeys.BLOCKED_COUNTER_PREFIX);
    }

    SecurityListMaintainJob(JdbcTemplate jdbcTemplate, StringRedisTemplate redis, SecurityListWriter listWriter,
                            ConfigService configService, RiskListCache listCache,
                            GatewaySnapshotPublisher snapshotPublisher, String blockedPrefix) {
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.listWriter = listWriter;
        this.configService = configService;
        this.listCache = listCache;
        this.snapshotPublisher = snapshotPublisher;
        this.blockedPrefix = blockedPrefix;
    }

    /** 一轮的结果 */
    public record Result(int escalated, int segments, long blocked) {

        @Override
        public String toString() {
            return String.format("黑到期转灰 %d 条，同段连坐拉灰 %d 段，同步被挡次数 %d", escalated, segments, blocked);
        }
    }

    @Override
    public String execute(SolvelaJobContext ctx) {
        return run().toString();
    }

    public Result run() {
        GateSettings settings = GateSettings.parse(configService.getConfigValue(ConfigKeyEnum.GATEWAY_SECURITY_GATE));
        int escalated = escalate(settings);
        int segments = segmentGrey(settings);
        long blocked = syncBlocked();
        if (escalated + segments > 0) {
            listCache.reload();
            snapshotPublisher.publishQuietly("安全名单维护");
        }
        return new Result(escalated, segments, blocked);
    }

    // ------------------------------------------------------------------ 黑到期转灰

    /** 一条到期的自动黑名单 */
    private record ExpiredBlack(long id, SecurityListDimensionEnum dimension, String value) {
    }

    private int escalate(GateSettings s) {
        List<ExpiredBlack> expired = jdbcTemplate.query("""
                SELECT id, dimension, list_value FROM t_security_list
                 WHERE list_type = 1 AND source = 2 AND status = 1 AND dimension IN (1, 2)
                   AND expire_time IS NOT NULL AND expire_time <= NOW()
                 ORDER BY id LIMIT ?
                """, (rs, i) -> new ExpiredBlack(rs.getLong("id"),
                SecurityListWriter.dimension(rs.getInt("dimension")), rs.getString("list_value")), ESCALATE_BATCH);
        long until = epochSecondsAfterDays(s.escalateGreyDays());
        expired.forEach(black -> escalateOne(black, until, s.escalateGreyDays()));
        if (!expired.isEmpty()) {
            log.info("【安全名单】黑到期转灰 {} 条", expired.size());
        }
        return expired.size();
    }

    /** 同一个值进灰名单，黑的那一行撤销（撤销理由写明是转灰，不是人工撤的） */
    private void escalateOne(ExpiredBlack black, long until, int greyDays) {
        listWriter.upsert(new SecurityListWriter.Entry(SecurityListTypeEnum.GREY, black.dimension(), black.value(),
                "ESCALATE", "自动黑名单到期，转灰 " + greyDays + " 天（自动）", until, null));
        jdbcTemplate.update("""
                UPDATE t_security_list
                   SET status = 0, revoke_operator = ?, revoke_reason = '到期，已转灰（ESCALATE）', revoke_time = NOW()
                 WHERE id = ? AND status = 1
                """, SecurityListWriter.SYSTEM, black.id());
    }

    // ------------------------------------------------------------------ 同段连坐

    private int segmentGrey(GateSettings s) {
        long until = epochSecondsAfterDays(s.segmentDays());
        int created = 0;
        for (Map.Entry<String, Set<String>> segment : recentlyBlackedBySegment(s).entrySet()) {
            int blackedIps = segment.getValue().size();
            if (blackedIps >= s.segmentMinIps() && greySegment(segment.getKey(), blackedIps, until, s)) {
                created++;
            }
        }
        return created;
    }

    /**
     * 最近被自动拉黑过的 IP，按所在的段分组。「被自动拉黑过」= 还有效的，或已被系统转灰的
     * （被人撤销的不算 —— 那多半是误判）。只看蜜罐、关键字、蜜罐账号这三种确凿的来源。
     */
    private Map<String, Set<String>> recentlyBlackedBySegment(GateSettings s) {
        List<String> values = jdbcTemplate.queryForList("""
                SELECT list_value FROM t_security_list
                 WHERE list_type = 1 AND source = 2 AND dimension IN (1, 2)
                   AND source_rule IN ('HONEYPOT', 'KEYWORD', 'CANARY')
                   AND (status = 1 OR revoke_operator = ?)
                   AND last_hit_time >= NOW() - INTERVAL ? SECOND
                """, String.class, SecurityListWriter.SYSTEM, s.segmentWithinSeconds());
        Map<String, Set<String>> bySegment = new HashMap<>();
        for (String value : values) {
            String address = value.contains("/") ? value.substring(0, value.indexOf('/')) : value;
            String segment = IpKeys.segmentListValue(address);
            if (segment != null) {
                bySegment.computeIfAbsent(segment, k -> new HashSet<>()).add(value);
            }
        }
        return bySegment;
    }

    /** 整段拉灰。🔴 只拉灰，永远不自动拉黑整段。返回是不是新拉的（已经在灰名单里的不算） */
    private boolean greySegment(String segment, int blackedIps, long until, GateSettings s) {
        SecurityListWriter.Outcome outcome = listWriter.upsert(new SecurityListWriter.Entry(SecurityListTypeEnum.GREY,
                SecurityListDimensionEnum.IP_RANGE, segment, "SEGMENT",
                "同段 " + blackedIps + " 个 IP 被自动拉黑，整段拉灰 " + s.segmentDays() + " 天（自动）", until, null), true);
        if (outcome.changed()) {
            log.warn("【安全名单】同段连坐：{} 里 {} 个 IP 被自动拉黑，整段拉灰 {} 天", segment, blackedIps, s.segmentDays());
        }
        return outcome.changed();
    }

    // ------------------------------------------------------------------ 被挡次数

    private long syncBlocked() {
        long total = 0;
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(blockedPrefix + "*").count(500).build())) {
            while (cursor.hasNext()) {
                total += syncBlocked(cursor.next());
            }
        } catch (RuntimeException e) {
            log.warn("【安全名单】同步被挡次数中途失败，已同步 {}，剩下的下一轮再来", total, e);
        }
        return total;
    }

    /** 一个计数键 {@code <前缀><维度>:<名单值>}：取走计数（原子地置 0），加到那一行黑名单上 */
    private long syncBlocked(String key) {
        String rest = key.substring(blockedPrefix.length());
        int colon = rest.indexOf(':');
        if (colon <= 0) {
            return 0;
        }
        String previous = redis.opsForValue().getAndSet(key, "0");
        long n = previous == null ? 0 : Long.parseLong(previous);
        if (n > 0) {
            jdbcTemplate.update("""
                    UPDATE t_security_list SET blocked_count = blocked_count + ?
                     WHERE dimension = ? AND list_value = ? AND list_type = 1
                    """, n, Integer.parseInt(rest.substring(0, colon)), rest.substring(colon + 1));
        }
        return Math.max(n, 0);
    }

    private static long epochSecondsAfterDays(int days) {
        return System.currentTimeMillis() / 1000 + days * 86400L;
    }
}
