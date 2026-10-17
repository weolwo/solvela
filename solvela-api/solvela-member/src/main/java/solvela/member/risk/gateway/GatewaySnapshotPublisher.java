package solvela.member.risk.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.base.module.config.ConfigChangedEvent;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.member.RateLimitRule;
import solvela.member.risk.dao.RateLimitRuleDao;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.SecurityKeys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把网关要的规则拼成一份快照，写进 Redis（docs/架构/网关阶梯限流-技术方案.md §6）。
 *
 * <h3>谁调它</h3>
 * <ul>
 *   <li>后台保存限流规则、改名单、改那两条全局配置之后 —— 立即发布，网关 15 秒内生效；</li>
 *   <li>{@link GatewaySnapshotPublishJob} 每 5 分钟 —— 自愈：Redis 被清了、被内存淘汰了、上一次发布失败了，都在这里补回来。</li>
 * </ul>
 *
 * <h3>🔴 全局参数直接查库，不走 {@code ConfigService} 的缓存</h3>
 * 那份缓存是<b>每个进程各一份</b>，只在本进程里写配置时刷新。后台开两个实例时，
 * 在 A 上改了配置，B 的缓存还是旧值 —— 由 B 发布的话，就把旧值又推给了网关。快照必须拿库里的真值。
 *
 * <h3>两个发布方同时写怎么办</h3>
 * 保存触发的发布与定时任务的发布可能交错：先读库的那个后写，就会把更新的快照盖回旧的。
 * 所以写入用一段脚本做「比较再写」：只有 {@code builtAt}（读库那一刻的<b>数据库时钟</b>，两边同一个钟）
 * 比 Redis 里的新才写。
 *
 * <h3>发布失败不影响保存</h3>
 * 后台改名单时，名单已经写进库了；Redis 一时写不进去，5 分钟后的定时任务会补上。
 * 让「保存」跟着 Redis 一起失败，用户会以为没存上、再点一次 —— 那才是麻烦。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewaySnapshotPublisher {

    /** 快照里搬过去的全局参数 */
    static final List<ConfigKeyEnum> CONFIG_KEYS =
            List.of(ConfigKeyEnum.GATEWAY_RATE_LIMIT, ConfigKeyEnum.GATEWAY_SECURITY_GATE);

    private static final Set<String> CONFIG_KEY_VALUES =
            Set.of(ConfigKeyEnum.GATEWAY_RATE_LIMIT.getValue(), ConfigKeyEnum.GATEWAY_SECURITY_GATE.getValue());

    /**
     * 比较再写：KEYS[1] = 快照哈希；ARGV = builtAt、version、body，以及这三个字段的字段名。
     * 返回 1 = 写了，0 = Redis 里已有更新的（或同一刻的）快照、没写。
     *
     * <p>🔴 用 {@code HMSET} 不用多对参数的 {@code HSET}：后者要 Redis 4.0，本地开发用的 Redis 不认
     * （报 Wrong number of args）—— 与 {@code RedisService} 里不用 {@code GETDEL} 是同一个坑。
     */
    private static final RedisScript<Long> PUBLISH = RedisScript.of("""
            local current = tonumber(redis.call('HGET', KEYS[1], ARGV[4]) or '-1')
            if tonumber(ARGV[1]) <= current then
                return 0
            end
            redis.call('HMSET', KEYS[1], ARGV[4], ARGV[1], ARGV[5], ARGV[2], ARGV[6], ARGV[3])
            return 1
            """, Long.class);

    private final RateLimitRuleDao ruleDao;

    private final JdbcTemplate jdbcTemplate;

    private final StringRedisTemplate redis;

    /** 一次发布的结果：给定时任务的执行日志用 */
    public record Outcome(String version, boolean written, int rules, int ipEntries) {
    }

    /**
     * 读库、拼快照、比较再写。异常向上抛 —— 由调用方决定要不要吞（见 {@link #publishQuietly}）。
     */
    public Outcome publish() {
        GatewayRuleSnapshot snapshot = build();
        Long written = redis.execute(PUBLISH, List.of(SecurityKeys.RULE_SNAPSHOT),
                String.valueOf(snapshot.builtAt()), snapshot.version(), JsonUtils.toJson(snapshot),
                SecurityKeys.SNAPSHOT_FIELD_BUILT_AT, SecurityKeys.SNAPSHOT_FIELD_VERSION, SecurityKeys.SNAPSHOT_FIELD_BODY);
        boolean ok = written != null && written == 1L;
        log.info("【网关快照】发布, 版本: {}, 规则 {} 条, IP 名单 {} 条, {}",
                snapshot.version(), snapshot.rules().size(), snapshot.ipLists().size(), ok ? "已写入" : "Redis 里已有更新的，未覆盖");
        return new Outcome(snapshot.version(), ok, snapshot.rules().size(), snapshot.ipLists().size());
    }

    /** 保存类操作之后调用：失败只记日志，定时任务会补（见类注释） */
    public void publishQuietly(String reason) {
        try {
            publish();
        } catch (Exception e) {
            log.error("【网关快照】{}之后发布失败，5 分钟内由定时任务补发", reason, e);
        }
    }

    /** 后台改了那两条全局配置 → 立即发布 */
    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.configKey() != null && CONFIG_KEY_VALUES.contains(event.configKey().toLowerCase())) {
            publishQuietly("修改配置 " + event.configKey());
        }
    }

    /** 拼一份快照。不写 Redis —— 单独暴露出来，测试拿它与网关的内置默认值比对 */
    public GatewayRuleSnapshot build() {
        long builtAt = dbNowMillis();

        List<GatewayRuleSnapshot.Rule> rules = ruleDao.selectList(null).stream()
                .sorted(Comparator.comparing(RateLimitRule::getTargetType)
                        .thenComparing(RateLimitRule::getTarget)
                        .thenComparing(RateLimitRule::getDimension))
                .map(r -> new GatewayRuleSnapshot.Rule(r.getTargetType(), r.getTarget(), r.getDimension(),
                        r.getBurst(), r.getLimit3m(), r.getLimit5m(), r.getLimit10m(),
                        r.getLongWindow(), r.getLimitLong(), r.getMode()))
                .toList();

        Map<String, String> configs = new LinkedHashMap<>();
        for (ConfigKeyEnum key : CONFIG_KEYS) {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM t_config WHERE config_key = ?", String.class, key.getValue());
            if (!values.isEmpty()) {
                configs.put(key.getValue(), values.get(0));
            }
        }

        /*
         * 🔴 到期过滤与「到期时间 → epoch」都在 SQL 里做：Java 这一侧不做任何时区换算（交接文档铁律 10）。
         * expire_time 是按会话时区写进库的，UNIX_TIMESTAMP 在同一个会话时区里还原 —— 两边落在同一个钟上。
         */
        List<GatewayRuleSnapshot.IpEntry> ipLists = jdbcTemplate.query("""
                        SELECT list_type, dimension, list_value, score, source,
                               CAST(UNIX_TIMESTAMP(expire_time) * 1000 AS SIGNED) AS expire_at
                        FROM t_security_list
                        WHERE status = ? AND dimension IN (?, ?)
                          AND (expire_time IS NULL OR expire_time > NOW())
                        ORDER BY id""",
                (rs, i) -> new GatewayRuleSnapshot.IpEntry(
                        rs.getInt("list_type"), rs.getInt("dimension"), rs.getString("list_value"),
                        (Integer) rs.getObject("score", Integer.class), rs.getInt("source"),
                        (Long) rs.getObject("expire_at", Long.class)),
                SecurityListStatusEnum.VALID.getValue(),
                SecurityListDimensionEnum.IP.getValue(), SecurityListDimensionEnum.IP_RANGE.getValue());

        // 会员灰名单（网关方案 §5.5）：网关把这些会员的账号维度阈值减半。会员名单必有到期，值是会员号
        List<GatewayRuleSnapshot.MemberEntry> memberGrey = jdbcTemplate.query("""
                        SELECT list_value, CAST(UNIX_TIMESTAMP(expire_time) * 1000 AS SIGNED) AS expire_at
                        FROM t_security_list
                        WHERE status = ? AND dimension = ? AND list_type = ?
                          AND (expire_time IS NULL OR expire_time > NOW())
                        ORDER BY id""",
                (rs, i) -> new GatewayRuleSnapshot.MemberEntry(Long.parseLong(rs.getString("list_value").trim()),
                        (Long) rs.getObject("expire_at", Long.class)),
                SecurityListStatusEnum.VALID.getValue(), SecurityListDimensionEnum.MEMBER.getValue(),
                solvela.enums.SecurityListTypeEnum.GREY.getValue());

        String version = digest(rules, configs, ipLists, memberGrey);
        return new GatewayRuleSnapshot(version, builtAt, rules, configs, ipLists, memberGrey);
    }

    /**
     * 内容摘要：不含 {@code builtAt}，所以内容不变时版本不变、网关不重载。
     * 取 SHA-256 前 12 位十六进制 —— 只用来判断「变没变」，不做任何安全用途。
     */
    static String digest(List<GatewayRuleSnapshot.Rule> rules, Map<String, String> configs,
                         List<GatewayRuleSnapshot.IpEntry> ipLists, List<GatewayRuleSnapshot.MemberEntry> memberGrey) {
        try {
            String content = JsonUtils.toJson(List.of(rules, configs, ipLists, memberGrey));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("快照摘要计算失败", e);
        }
    }

    /**
     * 数据库时钟的「现在」（epoch 毫秒）。两个发布方比先后用的是同一个钟 ——
     * 用各自 JVM 的钟的话，两台机器差几百毫秒就能让旧快照盖掉新快照。
     */
    private long dbNowMillis() {
        Long now = jdbcTemplate.queryForObject("SELECT CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS SIGNED)", Long.class);
        if (now == null) {
            throw new IllegalStateException("取不到数据库时钟");
        }
        return now;
    }
}
