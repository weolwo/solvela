package solvela.app.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import solvela.app.auth.CurrentDevice;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.ClientIp;
import solvela.auth.device.DeviceIdentity;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.SecurityKeys;

import java.io.IOException;
import java.util.List;

/**
 * 安全闸 ①：封禁检查（网关方案 §5.5）。被封的请求在这里结束 —— 不验会话、不计限流、不调下游。
 *
 * <h3>查四样东西，越便宜的越先查</h3>
 * <ol>
 *   <li><b>IP / IP 段黑名单</b>：来自规则快照，纯内存比 CIDR。🔴 <b>人工</b>条目不豁免老设备、也不看白名单 ——
 *       那是有人看过之后签的字；<b>自动</b>条目对老设备与白名单 IP 豁免（共用出口后面的真人带着自己的老设备）；</li>
 *   <li>进程内缓存（{@link PenaltyCache}）：最近判定为「在处罚中」的 IP / 设备；</li>
 *   <li>一次 Redis 往返（一段脚本）同时问三件事：
 *       IP 全局处罚 {@code sec:pen:ip:*}（豁免同第 1 条的自动条目）、设备全局处罚 {@code sec:pen:dev:*}、
 *       设备是否在封禁镜像 {@code sec:dev:banned} 里。IP 豁免、又没有设备的请求连这次往返都省了。</li>
 * </ol>
 * 限流阶梯的处罚<b>不在这里查</b>：它是按场景罚的（登录被罚不该连签到也挡），由 {@link RateLimiter} 的脚本第一步自己判。
 * 身份黑名单也不在这里：网关不知道请求里的身份是不是登录身份，它留在风控。
 *
 * <h3>为什么必须是过滤器</h3>
 * 要覆盖没有 Controller 的路径（蜜罐、扫描器探的 {@code /.env}），也要排在会话查询之前 —— 被封的请求连那一次 Redis 都不该花。
 * 排在 {@code DeviceFilter} 之后：要先知道设备令牌有多老，才能判老设备豁免。
 *
 * <h3>对外</h3>
 * 与限流<b>同一句话</b>：{@code 429 OPERATION_LIMITED}，有到期时间的带 {@code Retry-After}。
 * 告诉对方「你被拉黑了」等于给他一个精确的调试反馈。
 * 错误体交给 {@code ApiExceptionHandler}（经 {@link HandlerExceptionResolver}），全站错误响应仍只有那一个出口。
 *
 * <h3>🔴 Redis 出错就放行</h3>
 * 打 ERROR，按没有处罚处理：安全闸挂了不能等于整站挂了。内存里的黑名单不受影响。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 7)
public class SecurityGateFilter extends OncePerRequestFilter {

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

    private final RuleSnapshotHolder snapshotHolder;

    private final HandlerExceptionResolver exceptionResolver;

    private final PenaltyCache cache = new PenaltyCache();

    private volatile Derived derived;

    /** 显式构造器：Lombok 不会把 {@code @Qualifier} 抄到构造参数上 */
    public SecurityGateFilter(StringRedisTemplate redis, RuleSnapshotHolder snapshotHolder,
                              @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.redis = redis;
        this.snapshotHolder = snapshotHolder;
        this.exceptionResolver = exceptionResolver;
    }

    /** 快照换了才重算 */
    private record Derived(GatewayRuleSnapshot snapshot, IpLists lists, long oldDeviceSeconds) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        PenaltyCache.Blocked blocked = check(ClientIp.of(request), CurrentDevice.find().orElse(null));
        if (blocked == null) {
            chain.doFilter(request, response);
            return;
        }
        exceptionResolver.resolveException(request, response, null, limited(blocked));
    }

    /** 该挡返回处罚，放行返回 null */
    PenaltyCache.Blocked check(String clientIp, DeviceIdentity device) {
        Derived d = derived();
        long nowMillis = System.currentTimeMillis();
        long now = nowMillis / 1000;
        IpRange ip = IpRange.single(clientIp);
        boolean oldDevice = device != null && device.issuedAt() != null
                && now - device.issuedAt().getEpochSecond() >= d.oldDeviceSeconds();
        boolean ipExempt = oldDevice || d.lists().white(ip, nowMillis);

        // ① 黑名单：纯内存
        IpLists.BlackHit black = d.lists().black(ip, nowMillis);
        if (black != null && (black.manual() || !ipExempt)) {
            return new PenaltyCache.Blocked(black.expireAtMillis() == null ? 0 : black.expireAtMillis() / 1000);
        }

        // ② 进程内缓存
        String ipKey = ip == null ? null : IpKeys.ip(clientIp);
        boolean checkIp = ipKey != null && !ipExempt;
        String deviceId = device == null ? null : device.deviceId();
        String ipCacheKey = checkIp ? "ip:" + ipKey : null;
        String deviceCacheKey = deviceId == null ? null : "dev:" + deviceId;
        PenaltyCache.Blocked cached = ipCacheKey == null ? null : cache.get(ipCacheKey, nowMillis);
        if (cached == null && deviceCacheKey != null) {
            cached = cache.get(deviceCacheKey, nowMillis);
        }
        if (cached != null) {
            return cached;
        }
        if (!checkIp && deviceId == null) {
            return null;
        }

        // ③ 一次 Redis 往返
        List<?> r;
        try {
            r = redis.execute(SCRIPT,
                    List.of(SecurityKeys.securityPenaltyIp(checkIp ? ipKey : "-"),
                            SecurityKeys.securityPenaltyDevice(deviceId == null ? "-" : deviceId),
                            SecurityKeys.BANNED_DEVICES),
                    checkIp ? "1" : "0", deviceId == null ? "" : deviceId);
        } catch (Exception e) {
            log.error("【安全闸】查处罚失败，放行", e);
            return null;
        }
        if (r == null || r.size() < 3) {
            return null;
        }
        long ipUntil = toLong(r.get(0));
        long deviceUntil = toLong(r.get(1));
        boolean banned = toLong(r.get(2)) == 1;
        if (banned) {
            return remember(deviceCacheKey, new PenaltyCache.Blocked(0), nowMillis, "设备已封禁", deviceId);
        }
        if (deviceUntil > now) {
            return remember(deviceCacheKey, new PenaltyCache.Blocked(deviceUntil), nowMillis, "设备处罚中", deviceId);
        }
        if (ipUntil > now) {
            return remember(ipCacheKey, new PenaltyCache.Blocked(ipUntil), nowMillis, "IP 处罚中", ipKey);
        }
        return null;
    }

    private PenaltyCache.Blocked remember(String cacheKey, PenaltyCache.Blocked blocked, long nowMillis,
                                          String reason, String value) {
        // 只在「缓存里还没有」时打一行：被封的请求每秒几千次，逐条打日志等于替攻击者写日志
        log.info("【安全闸】挡下, 原因: {}, 值: {}, 到期: {}", reason, value,
                blocked.untilEpochSeconds() == 0 ? "无" : blocked.untilEpochSeconds());
        cache.put(cacheKey, blocked, nowMillis);
        return blocked;
    }

    private static ApiException limited(PenaltyCache.Blocked blocked) {
        if (blocked.untilEpochSeconds() == 0) {
            // 没有到期时间：不给倒计时，只说「稍后再试」
            return new ApiException(ApiErrors.OPERATION_LIMITED);
        }
        return RateLimiter.blocked(blocked.untilEpochSeconds() - System.currentTimeMillis() / 1000);
    }

    private Derived derived() {
        GatewayRuleSnapshot snapshot = snapshotHolder.current();
        Derived d = derived;
        if (d == null || d.snapshot() != snapshot) {
            RateLimitGlobals globals = RateLimitGlobals.parse(snapshot.configs() == null ? null
                    : snapshot.configs().get("gateway_rate_limit"));
            d = new Derived(snapshot, new IpLists(snapshot), globals.oldDeviceSeconds());
            derived = d;
        }
        return d;
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
    }
}
