package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import solvela.app.ratelimit.SecurityGateFilter.Probe;
import solvela.app.ratelimit.SecurityGateFilter.Trap;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;
import solvela.security.SlidingWindowScripts;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蜜罐与关键字共用的处罚：IP 与设备各罚 {@code ban}（作用于所有接口），写进本实例缓存（下一个请求就挡，不用去 Redis），
 * 并投一条事件（IP 被罚的，落库任务把它拉黑到同一时刻）。
 * <ul>
 *   <li><b>先过熔断</b>（第 2 道保险）：窗口内太多不同 IP 命中同一个来源 → 不罚、告警。
 *       更可能是我们自己出了问题：前端在请求蜜罐路径 / 发这类字符，或规则写宽了；</li>
 *   <li>🔴 命中的是<b>老设备</b>：只罚设备，IP 那一份压下（§5.2）—— 共用出口后面的真人不该因为别人而被封一天。
 *       设备那一份照罚：设备维度是它自己的账；</li>
 *   <li>🔴 IP 白名单<b>不豁免</b>：白名单只豁免限流（§6.3）—— 办公室出口去碰蜜罐，照样处罚，而且值得有人看。</li>
 * </ul>
 * 处罚写不进 Redis 时只记录：安全闸挂了不能等于整站挂了。
 *
 * @Date 2026-10-17
 */
@Slf4j
final class TrapPenalty {

    static final RedisScript<Long> DISTINCT = RedisScript.of(SlidingWindowScripts.DISTINCT, Long.class);

    /** 熔断集合的成员上限：内存的硬上限，远大于任何合理的阈值 */
    private static final int BREAKER_MAX_IPS = 10_000;

    /** 熔断告警的最小间隔：熔断期间每次命中都打一条 ERROR 等于没有 ERROR */
    private static final long BREAKER_ALARM_INTERVAL_MILLIS = 60_000;

    private final StringRedisTemplate redis;

    private final PenaltyCache cache;

    /** 为 null 时不投事件（只在测试里） */
    private final IncidentPublisher incidents;

    /** 熔断键的前缀：生产为空串；测试换一个，免得碰到本机网关真在用的熔断计数 */
    private final String breakerNamespace;

    /** 每个熔断键上一次告警的时刻 */
    private final ConcurrentHashMap<String, Long> lastBreakerAlarm = new ConcurrentHashMap<>();

    TrapPenalty(StringRedisTemplate redis, PenaltyCache cache, IncidentPublisher incidents, String breakerNamespace) {
        this.redis = redis;
        this.cache = cache;
        this.incidents = incidents;
        this.breakerNamespace = breakerNamespace;
    }

    /**
     * 命中了什么。
     *
     * @param source     「蜜罐」/「关键字」，只用于日志
     * @param code       蜜罐 / 规则编码
     * @param detail     日志里的那一句（路径、命中位置与片段）
     * @param breakerKey 这个来源的熔断键（不带测试前缀）
     */
    record Catch(String source, String code, String detail, String breakerKey) {
    }

    Trap penalise(Catch what, Probe probe, SecurityIncidentEvent.Builder event, CompiledSnapshot snapshot,
                  SecurityGateConfig gate) {
        long nowMillis = System.currentTimeMillis();
        String ipKey = IpRange.single(probe.clientIp()) == null ? null : IpKeys.ip(probe.clientIp());
        if (ipKey != null && breakerOpen(what, ipKey, nowMillis, gate)) {
            log.warn("【{}】熔断中，只记录不处罚, 规则: {}, {}, IP: {}", what.source(), what.code(), what.detail(),
                    probe.clientIp());
            record(event.suppressed(SecurityIncidentEvent.SUPPRESS_BREAKER), probe);
            return Trap.BREAKER_OPEN;
        }
        if (ipKey != null && snapshot.oldDevice(probe.deviceIssuedAt(), nowMillis / 1000)) {
            ipKey = null;
            event.suppressed(SecurityIncidentEvent.SUPPRESS_OLD_DEVICE);
        }
        long until = nowMillis / 1000 + gate.banSeconds();
        event.penalty(null, until);
        if (ipKey != null) {
            event.action(SecurityIncidentEvent.ACTION_BLACK, until);
        }
        boolean written = write(what, probe, ipKey, until, gate.banSeconds());
        if (written) {
            remember(probe, ipKey, until, nowMillis);
        }
        record(event, probe);
        if (written) {
            logPenalty(what, probe, gate.banSeconds(), snapshot.ipLists().white(IpRange.single(probe.clientIp()), nowMillis));
        }
        return Trap.PENALISED;
    }

    /** 投一条事件（被压下的、或罚过的）。请求上的东西从 {@code probe} 补齐 */
    void record(SecurityIncidentEvent.Builder event, Probe probe) {
        if (incidents == null) {
            return;
        }
        incidents.publish(event.who(probe.clientIp(), probe.deviceId(), null)
                .request(probe.method(), probe.path(), probe.userAgent(), probe.traceId())
                .build());
    }

    /** 把这个 IP 记进熔断窗口，返回「是否已经熔断」 */
    private boolean breakerOpen(Catch what, String ipKey, long nowMillis, SecurityGateConfig gate) {
        String breakerKey = breakerNamespace + what.breakerKey();
        Long distinct;
        try {
            distinct = redis.execute(DISTINCT, List.of(breakerKey), ipKey,
                    String.valueOf(gate.breakerWindowSeconds() * 1000), String.valueOf(nowMillis),
                    String.valueOf(BREAKER_MAX_IPS));
        } catch (Exception e) {
            // 数不了就不知道是不是在集中命中 —— 照常处罚：会碰到这些的本来就只有扫描器
            log.error("【安全闸】熔断计数失败，按未熔断处理, {} {}", what.source(), what.code(), e);
            return false;
        }
        if (distinct == null || distinct <= gate.breakerDistinctIps()) {
            return false;
        }
        alarmBreaker(breakerKey, what, distinct, nowMillis, gate);
        return true;
    }

    private void alarmBreaker(String breakerKey, Catch what, long distinct, long nowMillis, SecurityGateConfig gate) {
        Long previous = lastBreakerAlarm.get(breakerKey);
        if (previous != null && nowMillis - previous < BREAKER_ALARM_INTERVAL_MILLIS) {
            return;
        }
        lastBreakerAlarm.put(breakerKey, nowMillis);
        log.error("【告警】【安全闸】熔断：{} {}，{} 秒内 {} 个不同 IP 命中（阈值 {}），停止处罚。"
                        + "更可能是我们自己出了问题：前端在请求蜜罐路径 / 发这类字符，规则写宽了，或蜜罐路径被公开链接了",
                what.source(), what.code(), gate.breakerWindowSeconds(), distinct, gate.breakerDistinctIps());
    }

    /** 写处罚；写不进去返回 false（这次只记录） */
    private boolean write(Catch what, Probe probe, String ipKey, long until, long banSeconds) {
        Duration ttl = Duration.ofSeconds(banSeconds);
        try {
            if (ipKey != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyIp(ipKey), String.valueOf(until), ttl);
            }
            if (probe.deviceId() != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyDevice(probe.deviceId()),
                        String.valueOf(until), ttl);
            }
            return true;
        } catch (Exception e) {
            log.error("【{}】处罚写不进 Redis，这次只记录, 规则: {}, IP: {}", what.source(), what.code(), probe.clientIp(), e);
            return false;
        }
    }

    /** 写进本实例缓存：下一个请求就挡，不用等去 Redis */
    private void remember(Probe probe, String ipKey, long until, long nowMillis) {
        if (ipKey != null) {
            cache.putIp(ipKey, new PenaltyCache.Blocked(until, IpKeys.listValue(probe.clientIp())), nowMillis);
        }
        if (probe.deviceId() != null) {
            cache.putDevice(probe.deviceId(), new PenaltyCache.Blocked(until), nowMillis);
        }
    }

    private static void logPenalty(Catch what, Probe probe, long banSeconds, boolean whiteIp) {
        if (whiteIp) {
            log.error("【告警】【{}】白名单 IP 命中（照样处罚，白名单只豁免限流）, 规则: {}, {}, IP: {}, 设备: {}",
                    what.source(), what.code(), what.detail(), probe.clientIp(), probe.deviceId());
            return;
        }
        log.warn("【{}】命中，IP 与设备处罚 {} 秒, 规则: {}, {}, IP: {}, 设备: {}",
                what.source(), banSeconds, what.code(), what.detail(), probe.clientIp(), probe.deviceId());
    }
}
