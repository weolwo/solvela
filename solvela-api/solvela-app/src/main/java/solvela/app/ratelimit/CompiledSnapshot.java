package solvela.app.ratelimit;

import solvela.security.GatewayRuleSnapshot;
import solvela.security.RateLimitDimension;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 一份规则快照编译成的查找表：限流与安全闸共用，<b>快照换了才重编一次</b>（由 {@link RuleSnapshotHolder} 负责）。
 *
 * <p>请求路径上只查表，不再解析 JSON、不再逐行扫规则。
 *
 * @Date 2026-10-17
 */
final class CompiledSnapshot {

    /** 快照 {@code configs} 里的两份全局配置（{@code t_config} 的键） */
    static final String RATE_LIMIT_CONFIG = "gateway_rate_limit";

    static final String SECURITY_GATE_CONFIG = "gateway_security_gate";

    /** {@code t_rate_limit_rule.target_type}：2 = 按接口覆盖，其余按场景 */
    private static final int TARGET_ENDPOINT = 2;

    private final GatewayRuleSnapshot snapshot;

    private final Map<String, Map<RateLimitDimension, GatewayRuleSnapshot.Rule>> sceneRules = new HashMap<>();

    private final Map<String, Map<RateLimitDimension, GatewayRuleSnapshot.Rule>> endpointRules = new HashMap<>();

    /** 会员灰名单：会员号 → 到期（epoch 毫秒，null = 长期） */
    private final Map<Long, Long> memberGrey = new HashMap<>();

    private final IpLists ipLists;

    private final RateLimitGlobals globals;

    private final SecurityGateConfig gate;

    CompiledSnapshot(GatewayRuleSnapshot snapshot) {
        this.snapshot = snapshot;
        for (GatewayRuleSnapshot.Rule rule : snapshot.rules()) {
            RateLimitDimension dimension = RateLimitDimension.of(rule.dimension());
            if (dimension == null) {
                continue;
            }
            Map<String, Map<RateLimitDimension, GatewayRuleSnapshot.Rule>> byTarget =
                    rule.targetType() == TARGET_ENDPOINT ? endpointRules : sceneRules;
            byTarget.computeIfAbsent(rule.target(), k -> new EnumMap<>(RateLimitDimension.class)).put(dimension, rule);
        }
        if (snapshot.memberGreyList() != null) {
            snapshot.memberGreyList().forEach(e -> memberGrey.put(e.memberId(), e.expireAt()));
        }
        this.ipLists = new IpLists(snapshot);
        this.globals = RateLimitGlobals.parse(config(RATE_LIMIT_CONFIG));
        this.gate = SecurityGateConfig.parse(config(SECURITY_GATE_CONFIG));
    }

    GatewayRuleSnapshot snapshot() {
        return snapshot;
    }

    IpLists ipLists() {
        return ipLists;
    }

    RateLimitGlobals globals() {
        return globals;
    }

    /** 原样解析出来的安检门配置，还没剔除与真实路由重叠的蜜罐（那要看网关自己的路由，见 {@code SecurityGateFilter}） */
    SecurityGateConfig gate() {
        return gate;
    }

    /** 一个请求的生效规则：场景的行打底，接口的行覆盖同一维度（并且单独计数） */
    Map<RateLimitDimension, GatewayRuleSnapshot.Rule> rulesFor(RateLimitScene scene, String endpointKey) {
        Map<RateLimitDimension, GatewayRuleSnapshot.Rule> result = new EnumMap<>(RateLimitDimension.class);
        result.putAll(sceneRules.getOrDefault(scene.name(), Map.of()));
        if (endpointKey != null) {
            result.putAll(endpointRules.getOrDefault(endpointKey, Map.of()));
        }
        return result;
    }

    boolean memberGrey(Long memberId, long nowMillis) {
        if (memberId == null || !memberGrey.containsKey(memberId)) {
            return false;
        }
        Long expireAt = memberGrey.get(memberId);
        return expireAt == null || expireAt > nowMillis;
    }

    /** 老设备：令牌签发满 {@code oldDeviceDays}。共用出口后面的真人带着自己的老设备，IP 维度对它们豁免 */
    boolean oldDevice(Instant deviceIssuedAt, long nowSeconds) {
        return deviceIssuedAt != null && nowSeconds - deviceIssuedAt.getEpochSecond() >= globals.oldDeviceSeconds();
    }

    private String config(String key) {
        return snapshot.configs() == null ? null : snapshot.configs().get(key);
    }
}
