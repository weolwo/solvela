package solvela.app.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.SecurityKeys;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关的规则快照：读不到用默认值、读到了换上、坏了不换、没了不退回默认值。连真 Redis。
 *
 * <p>用一个测试专用的快照键 —— 不碰本机网关真在用的 {@code gw:rl:snapshot}。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class RuleSnapshotHolderTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private StringRedisTemplate redis;

    private String key;

    private String instance;

    @BeforeEach
    void setUp() {
        key = "test:gw:rl:snapshot:" + UUID.randomUUID();
        instance = "test-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        redis.delete(List.of(key, SecurityKeys.loadedVersion(instance)));
    }

    @Test
    @DisplayName("Redis 里没有快照 → 用内置默认值，而且默认值里有规则（不会「不限流」）")
    void builtinWhenMissing() {
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, key, instance);
        holder.refresh();

        assertEquals(RuleSnapshotHolder.Source.BUILTIN, holder.source());
        assertEquals("builtin", holder.current().version());
        assertEquals(28, holder.current().rules().size(), "内置默认值应与种子数据同一套（28 条场景行）");
        assertTrue(holder.current().configs().containsKey("gateway_rate_limit"));
    }

    @Test
    @DisplayName("有快照 → 换上；版本变了 → 再换；同一版本 → 不重复解析")
    void loadsAndReloadsOnVersionChange() {
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, key, instance);
        publish(snapshot("v1", 7));
        holder.refresh();
        assertEquals(RuleSnapshotHolder.Source.REDIS, holder.source());
        assertEquals("v1", holder.current().version());
        assertEquals(7, holder.current().rules().get(0).burst());

        // 同一版本、内容被人改了：不该重新拉 —— 版本才是「变没变」的唯一判据
        redis.opsForHash().put(key, SecurityKeys.SNAPSHOT_FIELD_BODY, json(snapshot("v1", 99)));
        holder.refresh();
        assertEquals(7, holder.current().rules().get(0).burst());

        publish(snapshot("v2", 11));
        holder.refresh();
        assertEquals("v2", holder.current().version());
        assertEquals(11, holder.current().rules().get(0).burst());
    }

    @Test
    @DisplayName("🔴 新快照解析不了 / 一条规则都没有 → 不换，继续用手里那份")
    void keepsCurrentOnBadSnapshot() {
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, key, instance);
        publish(snapshot("v1", 7));
        holder.refresh();

        redis.opsForHash().putAll(key, Map.of(
                SecurityKeys.SNAPSHOT_FIELD_VERSION, "v2",
                SecurityKeys.SNAPSHOT_FIELD_BODY, "{这不是 JSON"));
        holder.refresh();
        assertEquals("v1", holder.current().version(), "解析失败时换上任何东西都比保留旧的糟");

        publish(new GatewayRuleSnapshot("v3", 3L, List.of(), Map.of(), List.of()));
        holder.refresh();
        assertEquals("v1", holder.current().version(), "一份没有规则的快照等于不限流，必须拒绝");
    }

    @Test
    @DisplayName("🔴 快照后来没了 → 继续用上一次加载的，不退回内置默认值")
    void keepsLastLoadedWhenSnapshotDisappears() {
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, key, instance);
        publish(snapshot("v1", 7));
        holder.refresh();

        redis.delete(key);
        holder.refresh();

        assertEquals(RuleSnapshotHolder.Source.REDIS, holder.source());
        assertEquals("v1", holder.current().version(), "后台调过的阈值不该因为 Redis 里的键没了就丢掉");
    }

    @Test
    @DisplayName("每次检查都回报自己加载了哪个版本，带过期时间（实例停了自己消失）")
    void reportsLoadedVersion() throws Exception {
        RuleSnapshotHolder holder = new RuleSnapshotHolder(redis, key, instance);
        publish(snapshot("v1", 7));
        holder.refresh();

        String loadedKey = SecurityKeys.loadedVersion(instance);
        Map<?, ?> loaded = MAPPER.readValue(redis.opsForValue().get(loadedKey), Map.class);
        assertEquals("v1", loaded.get("version"));
        assertEquals("REDIS", loaded.get("source"));
        Long ttl = redis.getExpire(loadedKey, TimeUnit.SECONDS);
        assertTrue(ttl != null && ttl > 0 && ttl <= RuleSnapshotHolder.LOADED_TTL.toSeconds(), "TTL = " + ttl);
    }

    private static GatewayRuleSnapshot snapshot(String version, int burst) {
        GatewayRuleSnapshot.Rule rule = new GatewayRuleSnapshot.Rule(
                1, "LOGIN", 1, burst, 30, 45, 60, "1h", 60, 2);
        return new GatewayRuleSnapshot(version, 1L, List.of(rule), Map.of("gateway_rate_limit", "{}"), List.of());
    }

    private void publish(GatewayRuleSnapshot snapshot) {
        redis.opsForHash().putAll(key, Map.of(
                SecurityKeys.SNAPSHOT_FIELD_VERSION, snapshot.version(),
                SecurityKeys.SNAPSHOT_FIELD_BUILT_AT, String.valueOf(snapshot.builtAt()),
                SecurityKeys.SNAPSHOT_FIELD_BODY, json(snapshot)));
    }

    private static String json(GatewayRuleSnapshot snapshot) {
        return MAPPER.writeValueAsString(snapshot);
    }
}
