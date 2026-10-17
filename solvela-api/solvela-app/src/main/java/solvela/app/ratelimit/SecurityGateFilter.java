package solvela.app.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import solvela.app.auth.CurrentDevice;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.ClientIp;
import solvela.auth.device.DeviceIdentity;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.SecurityKeys;
import solvela.security.SlidingWindowScripts;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全闸（网关方案 §5.5）：① 查封禁 → ② 蜜罐路径。越便宜的检查越往前。
 *
 * <h2>① 查封禁：被封的请求在这里结束 —— 不验会话、不计限流、不调下游</h2>
 * <ol>
 *   <li><b>IP / IP 段黑名单</b>：来自规则快照，纯内存比 CIDR。🔴 <b>人工</b>条目不豁免老设备 ——
 *       那是有人看过之后签的字；<b>自动</b>条目对老设备豁免（共用出口后面的真人带着自己的老设备）；</li>
 *   <li>进程内缓存（{@link PenaltyCache}）：最近判定为「在处罚中」的 IP / 设备；</li>
 *   <li>一次 Redis 往返（一段脚本）同时问三件事：
 *       IP 全局处罚 {@code sec:pen:ip:*}（老设备豁免）、设备全局处罚 {@code sec:pen:dev:*}、
 *       设备是否在封禁镜像 {@code sec:dev:banned} 里。不需要问 IP（老设备）、又没带设备的请求连这次往返都省了。</li>
 * </ol>
 * 🔴 <b>IP 白名单在这里不豁免任何东西</b>：白名单只豁免限流（§6.3）—— 办公室出口去碰蜜罐，照样处罚，而且值得有人看。
 * 限流阶梯的处罚也不在这里查：它是按场景罚的（登录被罚不该连签到也挡），由 {@link RateLimiter} 的脚本自己判。
 * 身份黑名单也不在这里：网关不知道请求里的身份是不是登录身份，它留在风控。
 *
 * <h2>② 蜜罐路径：真实客户端永远不会调用的路径</h2>
 * 命中 → IP 与设备各罚 {@code ban}（默认 24 小时），作用于所有接口。然后<b>照常往下走</b>：
 * 这些路径没有 Controller，请求自然落到「找不到」—— 响应与访问任何一个不存在的路径<b>逐字节相同</b>，
 * 因为它就是同一条路径产生的，而不是我们模仿出来的。对方要到下一个请求才开始收到 429。
 *
 * <p>三道保险（§5.3），防止蜜罐被拿来误伤：
 * <ol>
 *   <li><b>跨站发起的不罚</b>：别的网站一个图片标签就能让访客的浏览器来碰蜜罐，罚了就是替攻击者封人。
 *       只认 {@code Sec-Fetch-Site} 为 {@code same-origin} / {@code none}，或两个头（它与 {@code Origin}）都没有的非浏览器请求；</li>
 *   <li><b>集中命中熔断</b>：窗口内太多不同 IP 命中 → 更可能是我们自己的前端出了 bug，停止处罚、只记录、告警；</li>
 *   <li><b>不许与真实路由重叠</b>：启动时检查，重叠就启动失败（{@link #verifyHoneypotsAtStartup}）；
 *       运行中加载到重叠的条目，丢掉那几条并告警。</li>
 * </ol>
 *
 * <h2>为什么必须是过滤器</h2>
 * 要覆盖没有 Controller 的路径（蜜罐、扫描器探的 {@code /.env}），也要排在会话查询之前 —— 被封的请求连那一次 Redis 都不该花。
 * 排在 {@code DeviceFilter} 之后：要先知道设备令牌有多老，才能判老设备豁免。
 *
 * <h2>对外</h2>
 * 被挡与限流<b>同一句话</b>：{@code 429 OPERATION_LIMITED}，有到期时间的带 {@code Retry-After}。
 * 错误体交给 {@code ApiExceptionHandler}（经 {@link HandlerExceptionResolver}），全站错误响应仍只有那一个出口。
 *
 * <h2>🔴 Redis 出错就放行</h2>
 * 打 ERROR，按没有处罚处理；蜜罐命中写不进处罚也只打日志：安全闸挂了不能等于整站挂了。内存里的黑名单不受影响。
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

    static final RedisScript<Long> DISTINCT = RedisScript.of(SlidingWindowScripts.DISTINCT, Long.class);

    /** 熔断集合的成员上限：内存的硬上限，远大于任何合理的阈值 */
    private static final int BREAKER_MAX_IPS = 10_000;

    /** 熔断告警的最小间隔：熔断期间每次命中都打一条 ERROR 等于没有 ERROR */
    private static final long BREAKER_ALARM_INTERVAL_MILLIS = 60_000;

    private final StringRedisTemplate redis;

    private final RuleSnapshotHolder snapshotHolder;

    private final HandlerExceptionResolver exceptionResolver;

    /** 真实路由的来源；为 null 时不做重叠检查（只在测试里） */
    private final RequestMappingHandlerMapping handlerMapping;

    private final String breakerKey;

    private final PenaltyCache cache = new PenaltyCache();

    private final AtomicLong lastBreakerAlarm = new AtomicLong();

    private volatile Derived derived;

    /**
     * 显式构造器：Lombok 不会把 {@code @Qualifier} 抄到构造参数上。
     * 🔴 必须标 {@code @Autowired}：另有一个给测试换熔断键的构造器，不标的话 Spring 两个都不选、去找无参构造器
     */
    @Autowired
    public SecurityGateFilter(StringRedisTemplate redis, RuleSnapshotHolder snapshotHolder,
                              @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
                              @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this(redis, snapshotHolder, exceptionResolver, handlerMapping, SecurityKeys.HONEYPOT_BREAKER);
    }

    SecurityGateFilter(StringRedisTemplate redis, RuleSnapshotHolder snapshotHolder,
                       HandlerExceptionResolver exceptionResolver, RequestMappingHandlerMapping handlerMapping,
                       String breakerKey) {
        this.redis = redis;
        this.snapshotHolder = snapshotHolder;
        this.exceptionResolver = exceptionResolver;
        this.handlerMapping = handlerMapping;
        this.breakerKey = breakerKey;
    }

    /** 快照换了才重算 */
    private record Derived(GatewayRuleSnapshot snapshot, IpLists lists, long oldDeviceSeconds,
                           SecurityGateConfig gate) {
    }

    /** 一次蜜罐命中的结果 */
    enum Trap { PENALISED, CROSS_SITE, BREAKER_OPEN, NOT_A_HONEYPOT }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = ClientIp.of(request);
        DeviceIdentity device = CurrentDevice.find().orElse(null);
        PenaltyCache.Blocked blocked = check(ip, device);
        if (blocked != null) {
            exceptionResolver.resolveException(request, response, null, limited(blocked));
            return;
        }
        trap(request.getRequestURI(), ip, device, request.getHeader("Sec-Fetch-Site"), request.getHeader("Origin"));
        // 蜜罐也照常往下走：落到「找不到」，与任何不存在的路径同一个响应
        chain.doFilter(request, response);
    }

    // ================================================================== ① 查封禁

    /** 该挡返回处罚，放行返回 null */
    PenaltyCache.Blocked check(String clientIp, DeviceIdentity device) {
        Derived d = derived();
        long nowMillis = System.currentTimeMillis();
        long now = nowMillis / 1000;
        IpRange ip = IpRange.single(clientIp);
        boolean oldDevice = isOld(device, now, d);

        // 黑名单：纯内存
        IpLists.BlackHit black = d.lists().black(ip, nowMillis);
        if (black != null && (black.manual() || !oldDevice)) {
            return new PenaltyCache.Blocked(black.expireAtMillis() == null ? 0 : black.expireAtMillis() / 1000);
        }

        // 进程内缓存
        String ipKey = ip == null ? null : IpKeys.ip(clientIp);
        boolean checkIp = ipKey != null && !oldDevice;
        String deviceId = device == null ? null : device.deviceId();
        String ipCacheKey = checkIp ? ipCacheKey(ipKey) : null;
        String deviceCacheKey = deviceId == null ? null : deviceCacheKey(deviceId);
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

        // 一次 Redis 往返
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

    // ================================================================== ② 蜜罐

    /**
     * 请求路径是蜜罐的话，按三道保险决定罚不罚。
     *
     * @param secFetchSite 浏览器带的 {@code Sec-Fetch-Site}；非浏览器为 null
     * @param origin       {@code Origin}；没有为 null
     */
    Trap trap(String requestPath, String clientIp, DeviceIdentity device, String secFetchSite, String origin) {
        Derived d = derived();
        SecurityGateConfig.Honeypot honeypot = d.gate().honeypot(requestPath);
        if (honeypot == null) {
            return Trap.NOT_A_HONEYPOT;
        }
        String deviceId = device == null ? null : device.deviceId();
        if (!sameOriginOrNonBrowser(secFetchSite, origin)) {
            log.warn("【蜜罐】跨站发起的命中，只记录不处罚, 蜜罐: {}, 路径: {}, IP: {}, Sec-Fetch-Site: {}, Origin: {}",
                    honeypot.code(), requestPath, clientIp, secFetchSite, origin);
            return Trap.CROSS_SITE;
        }
        String ipKey = IpRange.single(clientIp) == null ? null : IpKeys.ip(clientIp);
        long nowMillis = System.currentTimeMillis();
        if (ipKey != null && breakerOpen(ipKey, nowMillis, d.gate())) {
            log.warn("【蜜罐】熔断中，只记录不处罚, 蜜罐: {}, 路径: {}, IP: {}", honeypot.code(), requestPath, clientIp);
            return Trap.BREAKER_OPEN;
        }
        long until = nowMillis / 1000 + d.gate().banSeconds();
        boolean white = d.lists().white(IpRange.single(clientIp), nowMillis);
        try {
            if (ipKey != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyIp(ipKey), String.valueOf(until),
                        java.time.Duration.ofSeconds(d.gate().banSeconds()));
            }
            if (deviceId != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyDevice(deviceId), String.valueOf(until),
                        java.time.Duration.ofSeconds(d.gate().banSeconds()));
            }
        } catch (Exception e) {
            log.error("【蜜罐】处罚写不进 Redis，这次只记录, 蜜罐: {}, IP: {}", honeypot.code(), clientIp, e);
            return Trap.PENALISED;
        }
        // 写进本实例的缓存：下一个请求就挡，不用等它去 Redis
        if (ipKey != null) {
            cache.put(ipCacheKey(ipKey), new PenaltyCache.Blocked(until), nowMillis);
        }
        if (deviceId != null) {
            cache.put(deviceCacheKey(deviceId), new PenaltyCache.Blocked(until), nowMillis);
        }
        if (white) {
            log.error("【告警】【蜜罐】白名单 IP 碰了蜜罐（照样处罚，白名单只豁免限流）, 蜜罐: {}, 路径: {}, IP: {}, 设备: {}",
                    honeypot.code(), requestPath, clientIp, deviceId);
        } else {
            log.warn("【蜜罐】命中，IP 与设备处罚 {} 秒, 蜜罐: {}, 路径: {}, IP: {}, 设备: {}",
                    d.gate().banSeconds(), honeypot.code(), requestPath, clientIp, deviceId);
        }
        return Trap.PENALISED;
    }

    /**
     * 第 1 道保险。浏览器会带 {@code Sec-Fetch-Site}：只认同源与「用户自己在地址栏敲的」（{@code none}）。
     * 不带它的：没有 {@code Origin} 当作非浏览器（脚本、扫描器）；带着 {@code Origin} 的是老浏览器发起的跨源请求，不罚。
     */
    static boolean sameOriginOrNonBrowser(String secFetchSite, String origin) {
        if (secFetchSite != null) {
            return "same-origin".equalsIgnoreCase(secFetchSite) || "none".equalsIgnoreCase(secFetchSite);
        }
        return origin == null || origin.isBlank();
    }

    /** 第 2 道保险：把这个 IP 记进熔断窗口，返回「是否已经熔断」 */
    private boolean breakerOpen(String ipKey, long nowMillis, SecurityGateConfig gate) {
        Long distinct;
        try {
            distinct = redis.execute(DISTINCT, List.of(breakerKey), ipKey,
                    String.valueOf(gate.breakerWindowSeconds() * 1000), String.valueOf(nowMillis),
                    String.valueOf(BREAKER_MAX_IPS));
        } catch (Exception e) {
            // 数不了就不知道是不是在集中命中 —— 照常处罚：蜜罐本来就只有扫描器会碰
            log.error("【蜜罐】熔断计数失败，按未熔断处理", e);
            return false;
        }
        if (distinct == null || distinct <= gate.breakerDistinctIps()) {
            return false;
        }
        long last = lastBreakerAlarm.get();
        if (nowMillis - last >= BREAKER_ALARM_INTERVAL_MILLIS && lastBreakerAlarm.compareAndSet(last, nowMillis)) {
            log.error("【告警】【蜜罐】熔断：{} 秒内 {} 个不同 IP 命中蜜罐（阈值 {}），停止处罚。"
                            + "更可能是我们自己的前端在请求蜜罐路径，或某个蜜罐路径被公开链接了",
                    gate.breakerWindowSeconds(), distinct, gate.breakerDistinctIps());
        }
        return true;
    }

    /**
     * 第 3 道保险：启动时，当前快照里有任何一条蜜罐路径能匹配到真实接口 → 启动失败。
     * 否则一次路由调整就会把真实用户全部送进 24 小时。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifyHoneypotsAtStartup() {
        SecurityGateConfig gate = gateConfig(snapshotHolder.current());
        List<SecurityGateConfig.Honeypot> overlapping = gate.overlapping(routePatterns());
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("蜜罐路径与真实接口重叠，拒绝启动（真实用户会被当成扫描器封 24 小时）: "
                    + overlapping.stream().map(h -> h.code() + "=" + h.path()).toList()
                    + "。改 t_config.gateway_security_gate.honeypotPaths，或改接口路径");
        }
        log.info("【蜜罐】启用 {} 条蜜罐路径", gate.honeypots().size());
    }

    // ================================================================== 工具

    private static ApiException limited(PenaltyCache.Blocked blocked) {
        if (blocked.untilEpochSeconds() == 0) {
            // 没有到期时间：不给倒计时，只说「稍后再试」
            return new ApiException(ApiErrors.OPERATION_LIMITED);
        }
        return RateLimiter.blocked(blocked.untilEpochSeconds() - System.currentTimeMillis() / 1000);
    }

    private static boolean isOld(DeviceIdentity device, long now, Derived d) {
        return device != null && device.issuedAt() != null
                && now - device.issuedAt().getEpochSecond() >= d.oldDeviceSeconds();
    }

    private static String ipCacheKey(String ipKey) {
        return "ip:" + ipKey;
    }

    private static String deviceCacheKey(String deviceId) {
        return "dev:" + deviceId;
    }

    private Derived derived() {
        GatewayRuleSnapshot snapshot = snapshotHolder.current();
        Derived d = derived;
        if (d == null || d.snapshot() != snapshot) {
            RateLimitGlobals globals = RateLimitGlobals.parse(snapshot.configs() == null ? null
                    : snapshot.configs().get("gateway_rate_limit"));
            SecurityGateConfig gate = gateConfig(snapshot);
            List<SecurityGateConfig.Honeypot> overlapping = handlerMapping == null ? List.of()
                    : gate.overlapping(routePatterns());
            if (!overlapping.isEmpty()) {
                log.error("【告警】【蜜罐】新快照里的蜜罐路径与真实接口重叠，这几条不启用: {}",
                        overlapping.stream().map(h -> h.code() + "=" + h.path()).toList());
                gate = gate.without(overlapping);
            }
            d = new Derived(snapshot, new IpLists(snapshot), globals.oldDeviceSeconds(), gate);
            derived = d;
        }
        return d;
    }

    private static SecurityGateConfig gateConfig(GatewayRuleSnapshot snapshot) {
        return SecurityGateConfig.parse(snapshot.configs() == null ? null
                : snapshot.configs().get("gateway_security_gate"));
    }

    private Set<String> routePatterns() {
        Set<String> patterns = new TreeSet<>();
        if (handlerMapping != null) {
            for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
                patterns.addAll(info.getPatternValues());
            }
        }
        return patterns;
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
    }
}
