package solvela.app.ratelimit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.SecurityKeys;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 网关手里的那份限流规则快照（docs/架构/网关阶梯限流-技术方案.md §6）。
 *
 * <h3>请求路径上一次 Redis 都不碰</h3>
 * 规则在进程内存里；后台线程每 15 秒只读一次快照的 version，变了才拉整份 body。
 * 读规则的是每一个请求，所以它必须是一次字段读取，而不是一次网络往返。
 *
 * <h3>三种来源，没有「不限流」</h3>
 * <ol>
 *   <li><b>Redis 里的快照</b>：正常情况；</li>
 *   <li><b>上一次成功加载的快照</b>：Redis 里的快照后来没了 / 读不到 / 解析失败 —— 继续用手里这份，而不是退回默认值；
 *       后台刚调过的阈值不该因为 Redis 抖一下就丢掉；</li>
 *   <li><b>内置默认值</b>（{@code solvela-contract} 里的 {@code default-gateway-snapshot.json}，与种子数据同一套）：
 *       只在启动以来<b>从没</b>读到过快照时用。读不到默认值本身则启动失败 —— 那是打包出了问题。</li>
 * </ol>
 *
 * <h3>为什么自己开一个线程，不用 {@code @Scheduled}</h3>
 * 本进程刻意没开 {@code @EnableScheduling}（{@code AppApplication} 类注释：几个进程连同一个库，
 * 开了就会有任务跑两遍）。这里刷新的是<b>每个实例各自的本地缓存</b>，本来就该每个实例各跑各的 ——
 * 一个守护线程就够，不值得为它把整个调度器打开、再去担心别的 {@code @Scheduled} 被意外启用。
 *
 * <h3>回报自己加载了哪个版本</h3>
 * 每次检查后写 {@code gw:rl:loaded:{实例}}（TTL 60 秒）。后台规则页据此显示「3 个实例都已加载 v42」，
 * 不用猜改的规则生效没有；实例停了，键自己过期、页面上就少一个。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
public class RuleSnapshotHolder {

    /** 内置默认值在 classpath 上的位置（solvela-contract 打进来的） */
    static final String DEFAULT_RESOURCE = "/solvela/security/default-gateway-snapshot.json";

    static final Duration REFRESH_INTERVAL = Duration.ofSeconds(15);

    /** 已加载版本的回报：比刷新间隔长得多，偶尔晚一次不会让实例在页面上「消失」 */
    static final Duration LOADED_TTL = Duration.ofSeconds(60);

    /** 来源：给日志、给回报 */
    enum Source { BUILTIN, REDIS }

    /**
     * 快照协议「加字段向后兼容」：发布方先发版加了字段，这里不能因此解析失败。
     * 单独一个 mapper，不用本进程那个塑造 C 端 JSON 形状的（它把 Long 写成字符串）。
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final StringRedisTemplate redis;

    /** 快照键。生产固定是 {@link SecurityKeys#RULE_SNAPSHOT}；测试用别的键，免得动到本机真在用的那一份 */
    private final String snapshotKey;

    private final String instance;

    private volatile GatewayRuleSnapshot current;

    private volatile Source source;

    /** 「Redis 里没有快照」这件事只在状态变化时打日志：每 15 秒一条 ERROR 等于没有 ERROR */
    private volatile boolean missingReported;

    private ScheduledExecutorService executor;

    @Autowired
    public RuleSnapshotHolder(StringRedisTemplate redis) {
        this(redis, SecurityKeys.RULE_SNAPSHOT, defaultInstance());
    }

    RuleSnapshotHolder(StringRedisTemplate redis, String snapshotKey, String instance) {
        this.redis = redis;
        this.snapshotKey = snapshotKey;
        this.instance = instance;
        this.current = loadBuiltin();
        this.source = Source.BUILTIN;
    }

    /** 当前生效的快照。永不为 null */
    public GatewayRuleSnapshot current() {
        return current;
    }

    Source source() {
        return source;
    }

    String instance() {
        return instance;
    }

    @PostConstruct
    void start() {
        // 启动时同步读一次：别让刚起来的实例用默认值跑满第一个 15 秒
        refresh();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gw-rule-snapshot");
            t.setDaemon(true);
            return t;
        });
        long seconds = REFRESH_INTERVAL.toSeconds();
        executor.scheduleWithFixedDelay(this::refresh, seconds, seconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 检查一次：version 变了就拉整份、换上；然后回报自己加载的版本。
     *
     * <p>任何异常都只记日志：刷新失败的代价是「规则晚一点更新」，绝不能让这个线程死掉
     * （{@code scheduleWithFixedDelay} 遇到未捕获的异常会静默地停止后续所有执行）。
     */
    void refresh() {
        try {
            String version = (String) redis.opsForHash().get(snapshotKey, SecurityKeys.SNAPSHOT_FIELD_VERSION);
            if (version == null) {
                reportMissing();
            } else {
                missingReported = false;
                if (source != Source.REDIS || !Objects.equals(version, current.version())) {
                    load(version);
                }
            }
        } catch (Exception e) {
            log.error("【网关快照】读取失败，继续用手里这份（来源 {}，版本 {}）", source, current.version(), e);
        }
        reportLoaded();
    }

    private void load(String version) {
        String body = (String) redis.opsForHash().get(snapshotKey, SecurityKeys.SNAPSHOT_FIELD_BODY);
        if (body == null) {
            log.error("【网关快照】有版本 {} 却没有内容，继续用手里这份", version);
            return;
        }
        GatewayRuleSnapshot loaded = MAPPER.readValue(body, GatewayRuleSnapshot.class);
        if (loaded.rules() == null || loaded.rules().isEmpty()) {
            // 一份没有任何规则的快照几乎一定是发布方读库出了问题。换上它等于「不限流」
            log.error("【网关快照】版本 {} 里没有任何规则，拒绝加载，继续用手里这份", version);
            return;
        }
        String previous = current.version();
        current = loaded;
        source = Source.REDIS;
        log.info("【网关快照】已加载, 版本 {} → {}, 规则 {} 条, IP 名单 {} 条",
                previous, loaded.version(), loaded.rules().size(),
                loaded.ipLists() == null ? 0 : loaded.ipLists().size());
    }

    private void reportMissing() {
        if (missingReported) {
            return;
        }
        missingReported = true;
        if (source == Source.BUILTIN) {
            log.error("【网关快照】Redis 里没有快照，正在用内置默认值。后台的定时任务 gatewaySnapshotPublish 应该在 5 分钟内补上");
        } else {
            log.warn("【网关快照】Redis 里的快照不见了，继续用上一次加载的版本 {}", current.version());
        }
    }

    private void reportLoaded() {
        try {
            Map<String, Object> loaded = new LinkedHashMap<>();
            loaded.put("version", current.version());
            loaded.put("source", source.name());
            loaded.put("reportedAt", System.currentTimeMillis());
            redis.opsForValue().set(SecurityKeys.loadedVersion(instance), MAPPER.writeValueAsString(loaded), LOADED_TTL);
        } catch (Exception e) {
            log.warn("【网关快照】回报已加载版本失败", e);
        }
    }

    private static GatewayRuleSnapshot loadBuiltin() {
        try (InputStream in = RuleSnapshotHolder.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath 上没有 " + DEFAULT_RESOURCE + " —— solvela-contract 没打进来？");
            }
            return MAPPER.readValue(in, GatewayRuleSnapshot.class);
        } catch (IOException | tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("内置默认快照读不出来：" + DEFAULT_RESOURCE, e);
        }
    }

    /** 主机名:进程号。拿不到主机名时退回一个随机串 —— 只用来在页面上区分实例 */
    private static String defaultInstance() {
        String pid = String.valueOf(ProcessHandle.current().pid());
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + pid;
        } catch (Exception e) {
            String jvm = ManagementFactory.getRuntimeMXBean().getName();
            return jvm != null && !jvm.isBlank() ? jvm : UUID.randomUUID().toString().substring(0, 8) + ":" + pid;
        }
    }
}
