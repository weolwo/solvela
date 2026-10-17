package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import solvela.auth.device.DeviceIdentity;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.SecurityKeys;

import java.util.List;

/**
 * 安全闸第 ① 步：这个 IP / 设备此刻是不是被封着。三层，越便宜的越先问：
 * <ol>
 *   <li><b>IP / IP 段黑名单</b>：来自规则快照，纯内存比 CIDR。🔴 <b>人工</b>条目不豁免老设备 ——
 *       那是有人看过之后签的字；<b>自动</b>条目对老设备豁免（共用出口后面的真人带着自己的老设备）；</li>
 *   <li><b>进程内缓存</b>（{@link PenaltyCache}）：最近判定为「在处罚中」的 IP / 设备；</li>
 *   <li><b>一次 Redis 往返</b>（{@link #SCRIPT}）同时问三件事：IP 全局处罚（老设备不问）、设备全局处罚、设备是否在封禁镜像里。
 *       不问 IP、又没带设备的请求连这次往返都省了。</li>
 * </ol>
 * 🔴 Redis 出错就放行：打 ERROR，按没有处罚处理。安全闸挂了不能等于整站挂了。
 *
 * @Date 2026-10-17
 */
@Slf4j
final class BanCheck {

    /**
     * KEYS：IP 处罚、设备处罚、封禁集合。ARGV：查不查 IP（1/0）、设备号（空 = 不查设备）。
     * 返回 {@code {IP 处罚到期, 设备处罚到期, 是否封禁}}，到期是 epoch 秒，0 = 没有。
     */
    static final RedisScript<List> SCRIPT = RedisScript.of("""
            local r = {0, 0, 0}
            if ARGV[1] == '1' then
              r[1] = tonumber(redis.call('GET', KEYS[1]) or '0') or 0
            end
            if ARGV[2] ~= '' then
              r[2] = tonumber(redis.call('GET', KEYS[2]) or '0') or 0
              r[3] = redis.call('SISMEMBER', KEYS[3], ARGV[2])
            end
            return r
            """, List.class);

    private final StringRedisTemplate redis;

    private final PenaltyCache cache;

    BanCheck(StringRedisTemplate redis, PenaltyCache cache) {
        this.redis = redis;
        this.cache = cache;
    }

    /**
     * 要问的对象。
     *
     * @param ipKey    要查的 IP（{@code IpKeys.ip} 的写法）；老设备、解不开的 IP 为 null
     * @param deviceId 设备号；没带设备为 null
     */
    private record Subject(String clientIp, String ipKey, String deviceId) {

        boolean empty() {
            return ipKey == null && deviceId == null;
        }
    }

    /** 该挡返回处罚，放行返回 null */
    PenaltyCache.Blocked check(String clientIp, DeviceIdentity device, CompiledSnapshot snapshot) {
        long nowMillis = System.currentTimeMillis();
        boolean oldDevice = snapshot.oldDevice(device == null ? null : device.issuedAt(), nowMillis / 1000);
        IpRange ip = IpRange.single(clientIp);

        PenaltyCache.Blocked black = blacklisted(snapshot.ipLists().black(ip, nowMillis), oldDevice);
        if (black != null) {
            return black;
        }
        Subject subject = new Subject(clientIp, ip == null || oldDevice ? null : IpKeys.ip(clientIp),
                device == null ? null : device.deviceId());
        if (subject.empty()) {
            return null;
        }
        PenaltyCache.Blocked cached = cached(subject, nowMillis);
        return cached != null ? cached : lookup(subject, nowMillis);
    }

    /** 黑名单命中：人工条目一律挡，自动条目放过老设备 */
    private static PenaltyCache.Blocked blacklisted(IpLists.BlackHit hit, boolean oldDevice) {
        if (hit == null || (!hit.manual() && oldDevice)) {
            return null;
        }
        long until = hit.expireAtMillis() == null ? 0 : hit.expireAtMillis() / 1000;
        return new PenaltyCache.Blocked(until, hit.value());
    }

    private PenaltyCache.Blocked cached(Subject subject, long nowMillis) {
        PenaltyCache.Blocked byIp = cache.ip(subject.ipKey(), nowMillis);
        return byIp != null ? byIp : cache.device(subject.deviceId(), nowMillis);
    }

    private PenaltyCache.Blocked lookup(Subject subject, long nowMillis) {
        List<?> r = query(subject);
        if (r == null || r.size() < 3) {
            return null;
        }
        long now = nowMillis / 1000;
        long ipUntil = RateLimitScript.asLong(r.get(0));
        long deviceUntil = RateLimitScript.asLong(r.get(1));
        if (RateLimitScript.asLong(r.get(2)) == 1) {
            return rememberDevice(subject, new PenaltyCache.Blocked(0), nowMillis, "设备已封禁");
        }
        if (deviceUntil > now) {
            return rememberDevice(subject, new PenaltyCache.Blocked(deviceUntil), nowMillis, "设备处罚中");
        }
        if (ipUntil > now) {
            PenaltyCache.Blocked blocked = new PenaltyCache.Blocked(ipUntil, IpKeys.listValue(subject.clientIp()));
            logBlocked("IP 处罚中", subject.ipKey(), blocked);
            cache.putIp(subject.ipKey(), blocked, nowMillis);
            return blocked;
        }
        return null;
    }

    private List<?> query(Subject subject) {
        try {
            return redis.execute(SCRIPT,
                    List.of(SecurityKeys.securityPenaltyIp(subject.ipKey() == null ? "-" : subject.ipKey()),
                            SecurityKeys.securityPenaltyDevice(subject.deviceId() == null ? "-" : subject.deviceId()),
                            SecurityKeys.BANNED_DEVICES),
                    subject.ipKey() == null ? "0" : "1", subject.deviceId() == null ? "" : subject.deviceId());
        } catch (Exception e) {
            log.error("【安全闸】查处罚失败，放行", e);
            return null;
        }
    }

    private PenaltyCache.Blocked rememberDevice(Subject subject, PenaltyCache.Blocked blocked, long nowMillis,
                                                String reason) {
        logBlocked(reason, subject.deviceId(), blocked);
        cache.putDevice(subject.deviceId(), blocked, nowMillis);
        return blocked;
    }

    /** 只在「缓存里还没有」时打一行：被封的请求每秒几千次，逐条打日志等于替攻击者写日志 */
    private static void logBlocked(String reason, String value, PenaltyCache.Blocked blocked) {
        log.info("【安全闸】挡下, 原因: {}, 值: {}, 到期: {}", reason, value,
                blocked.untilEpochSeconds() == 0 ? "无" : blocked.untilEpochSeconds());
    }
}
