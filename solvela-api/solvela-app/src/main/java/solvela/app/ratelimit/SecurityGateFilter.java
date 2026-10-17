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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import solvela.app.auth.CurrentDevice;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.ClientIp;
import solvela.app.web.Trace;
import solvela.auth.device.DeviceIdentity;
import solvela.security.KeywordRules;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 安全闸（网关方案 §5.5）：① 查封禁 → ② 蜜罐路径 → ③ 关键字。越便宜的检查越往前。本类只管编排，每一步各有一个类：
 *
 * <h2>① 查封禁（{@link BanCheck}）：被封的请求在这里结束 —— 不验会话、不计限流、不调下游</h2>
 * IP / IP 段黑名单、全局处罚、设备封禁。限流阶梯的处罚不在这里查：它是按场景罚的（登录被罚不该连签到也挡），
 * 由 {@link RateLimiter} 的脚本自己判。身份黑名单也不在这里：网关不知道请求里的身份是不是登录身份，它留在风控。
 *
 * <h2>② 蜜罐路径：真实客户端永远不会调用的路径</h2>
 * 命中 → 罚（{@link TrapPenalty}）。然后<b>照常往下走</b>：这些路径没有 Controller，请求自然落到「找不到」——
 * 响应与访问任何一个不存在的路径<b>逐字节相同</b>，因为它就是同一条路径产生的，而不是我们模仿出来的。
 * 对方要到下一个请求才开始收到 429。三道保险防止蜜罐被拿来误伤：
 * <ol>
 *   <li><b>跨站发起的不罚</b>（{@link #sameOriginOrNonBrowser}）：别的网站一个图片标签就能让访客的浏览器来碰蜜罐；</li>
 *   <li><b>集中命中熔断</b>：见 {@link TrapPenalty}；</li>
 *   <li><b>不许与真实路由重叠</b>：启动时重叠就启动失败（{@link #verifyHoneypotsAtStartup}）；运行中加载到重叠的条目，丢掉那几条并告警。</li>
 * </ol>
 *
 * <h2>③ 关键字：参数里带着 {@code sleep(5)}、{@code union select} 的请求一看就不是正常用户</h2>
 * 找命中在 {@link KeywordScanner}，规则与反例集在 {@link KeywordRules}（后台保存时用同一份校验）。
 * 命中 → 罚法与蜜罐相同；对外与参数校验失败<b>逐字相同</b>（{@code 400 INVALID_ARGUMENT}），不告诉对方「你被拦了」。
 * 每条规则各自熔断，熔断时<b>放行</b>（那时挡下的多半是真人）。
 *
 * <h2>为什么必须是过滤器</h2>
 * 要覆盖没有 Controller 的路径（蜜罐、扫描器探的 {@code /.env}），也要排在会话查询之前 —— 被封的请求连那一次 Redis 都不该花。
 * 排在 {@code DeviceFilter} 之后：要先知道设备令牌有多老，才能判老设备豁免。
 *
 * <h2>对外</h2>
 * 被挡与限流<b>同一句话</b>：{@code 429 OPERATION_LIMITED}，有到期时间的带 {@code Retry-After}。
 * 错误体交给 {@code ApiExceptionHandler}（经 {@link HandlerExceptionResolver}），全站错误响应仍只有那一个出口。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 7)
public class SecurityGateFilter extends OncePerRequestFilter {

    private final RuleSnapshotHolder snapshotHolder;

    private final HandlerExceptionResolver exceptionResolver;

    /** 真实路由的来源；为 null 时不做重叠检查（只在测试里） */
    private final RequestMappingHandlerMapping handlerMapping;

    /** 被挡次数；为 null 时不计（只在测试里） */
    private final BlockedCounter blockedCounter;

    private final BanCheck banCheck;

    private final TrapPenalty penalty;

    /** 剔除了与真实路由重叠的蜜罐之后的安检门配置。快照换了才重算 */
    private volatile ActiveGate activeGate;

    /**
     * 显式构造器：Lombok 不会把 {@code @Qualifier} 抄到构造参数上。
     * 🔴 必须标 {@code @Autowired}：另有一个给测试换熔断键的构造器，不标的话 Spring 两个都不选、去找无参构造器
     */
    @Autowired
    public SecurityGateFilter(StringRedisTemplate redis, RuleSnapshotHolder snapshotHolder,
                              @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
                              @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                              IncidentPublisher incidents, BlockedCounter blockedCounter) {
        this(redis, snapshotHolder, exceptionResolver, handlerMapping, "", incidents, blockedCounter);
    }

    SecurityGateFilter(StringRedisTemplate redis, RuleSnapshotHolder snapshotHolder,
                       HandlerExceptionResolver exceptionResolver, RequestMappingHandlerMapping handlerMapping,
                       String breakerNamespace, IncidentPublisher incidents, BlockedCounter blockedCounter) {
        PenaltyCache cache = new PenaltyCache();
        this.snapshotHolder = snapshotHolder;
        this.exceptionResolver = exceptionResolver;
        this.handlerMapping = handlerMapping;
        this.blockedCounter = blockedCounter;
        this.banCheck = new BanCheck(redis, cache);
        this.penalty = new TrapPenalty(redis, cache, incidents, breakerNamespace);
    }

    /** 一个请求里安全闸要用到的东西。蜜罐与关键字的判定、事件都从这里取 */
    record Probe(String method, String path, String clientIp, DeviceIdentity device, String secFetchSite,
                 String origin, String userAgent, String traceId) {

        static Probe of(HttpServletRequest request, String clientIp, DeviceIdentity device) {
            return new Probe(request.getMethod(), request.getRequestURI(), clientIp, device,
                    request.getHeader("Sec-Fetch-Site"), request.getHeader("Origin"), request.getHeader("User-Agent"),
                    Trace.id());
        }

        String deviceId() {
            return device == null ? null : device.deviceId();
        }

        Instant deviceIssuedAt() {
            return device == null ? null : device.issuedAt();
        }

        /** 第 1 道保险的判据，见 {@link #sameOriginOrNonBrowser} */
        boolean crossSite() {
            return !sameOriginOrNonBrowser(secFetchSite, origin);
        }
    }

    /** 一次蜜罐 / 关键字检查的结果 */
    enum Trap { PENALISED, CROSS_SITE, BREAKER_OPEN, MISS }

    /** 某一份快照下生效的安检门配置 */
    private record ActiveGate(CompiledSnapshot source, SecurityGateConfig config) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = ClientIp.of(request);
        DeviceIdentity device = CurrentDevice.find().orElse(null);
        PenaltyCache.Blocked blocked = check(ip, device);
        if (blocked != null) {
            reject(request, response, blocked);
            return;
        }
        Probe probe = Probe.of(request, ip, device);
        if (trap(probe) != Trap.MISS) {
            // 蜜罐照常往下走：落到「找不到」，与任何不存在的路径同一个响应。关键字不用再扫了
            chain.doFilter(request, response);
            return;
        }
        byte[] body = KeywordScanner.readSmallJsonBody(request);
        KeywordRules.Hit hit = KeywordScanner.scan(gate().keywords(), request.getRequestURI(),
                request.getQueryString(), body, probe.userAgent());
        if (hit != null && keyword(hit, probe) != Trap.BREAKER_OPEN) {
            // 与参数校验失败逐字相同，不告诉对方「你被拦了」；之后的请求才开始收到 429
            exceptionResolver.resolveException(request, response, null, new ApiException(ApiErrors.INVALID_ARGUMENT));
            return;
        }
        chain.doFilter(body == null ? request : new CachedBodyRequest(request, body), response);
    }

    /** ① 查封禁：该挡返回处罚，放行返回 null */
    PenaltyCache.Blocked check(String clientIp, DeviceIdentity device) {
        return banCheck.check(clientIp, device, snapshotHolder.compiled());
    }

    /** ② 请求路径是蜜罐的话，跨站的只记录，其余交给 {@link TrapPenalty} */
    Trap trap(Probe probe) {
        SecurityGateConfig.Honeypot honeypot = gate().honeypot(probe.path());
        if (honeypot == null) {
            return Trap.MISS;
        }
        SecurityIncidentEvent.Builder event = SecurityIncidentEvent.builder(SecurityIncidentEvent.HONEYPOT, honeypot.code())
                .matched(SecurityIncidentEvent.FIELD_PATH, null, probe.path());
        if (probe.crossSite()) {
            log.warn("【蜜罐】跨站发起的命中，只记录不处罚, 蜜罐: {}, 路径: {}, IP: {}, Sec-Fetch-Site: {}, Origin: {}",
                    honeypot.code(), probe.path(), probe.clientIp(), probe.secFetchSite(), probe.origin());
            penalty.record(event.suppressed(SecurityIncidentEvent.SUPPRESS_CROSS_SITE), probe);
            return Trap.CROSS_SITE;
        }
        TrapPenalty.Catch what = new TrapPenalty.Catch("蜜罐", honeypot.code(), "路径 " + probe.path(),
                SecurityKeys.HONEYPOT_BREAKER);
        return penalty.penalise(what, probe, event, snapshotHolder.compiled(), gate());
    }

    /**
     * ③ 命中一条关键字之后罚不罚。路径与查询串上的跨站命中不罚（一个图片标签就能让访客的浏览器带着它来），
     * 请求体与 UA 不看这一条：JSON 请求体从别的网站发不过来（要过 CORS 预检），UA 浏览器里改不了。
     */
    Trap keyword(KeywordRules.Hit hit, Probe probe) {
        SecurityIncidentEvent.Builder event = SecurityIncidentEvent.builder(SecurityIncidentEvent.KEYWORD, hit.code())
                .matched(matchedField(hit.location()), matchedParam(hit.location()), hit.sample());
        if (urlBorne(hit.location()) && probe.crossSite()) {
            log.warn("【关键字】跨站发起的命中，拒绝但不处罚, 规则: {}, 位置: {}, 片段: {}, IP: {}",
                    hit.code(), hit.location(), hit.sample(), probe.clientIp());
            penalty.record(event.suppressed(SecurityIncidentEvent.SUPPRESS_CROSS_SITE), probe);
            return Trap.CROSS_SITE;
        }
        TrapPenalty.Catch what = new TrapPenalty.Catch("关键字", hit.code(),
                hit.location() + " 「" + hit.sample() + "」", SecurityKeys.keywordBreaker(hit.code()));
        return penalty.penalise(what, probe, event, snapshotHolder.compiled(), gate());
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

    /**
     * 第 3 道保险：启动时，当前快照里有任何一条蜜罐路径能匹配到真实接口 → 启动失败。
     * 否则一次路由调整就会把真实用户全部送进 24 小时。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifyHoneypotsAtStartup() {
        SecurityGateConfig gate = snapshotHolder.compiled().gate();
        List<SecurityGateConfig.Honeypot> overlapping = gate.overlapping(routePatterns());
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("蜜罐路径与真实接口重叠，拒绝启动（真实用户会被当成扫描器封 24 小时）: "
                    + describe(overlapping) + "。改 t_config.gateway_security_gate.honeypotPaths，或改接口路径");
        }
        log.info("【蜜罐】启用 {} 条蜜罐路径", gate.honeypots().size());
    }

    // ================================================================== 工具

    private void reject(HttpServletRequest request, HttpServletResponse response, PenaltyCache.Blocked blocked) {
        if (blockedCounter != null) {
            blockedCounter.record(blocked.listValue());
        }
        // 没有到期时间（设备封禁、长期黑名单）：不给倒计时，只说「稍后再试」
        ApiException limited = blocked.untilEpochSeconds() == 0 ? new ApiException(ApiErrors.OPERATION_LIMITED)
                : RateLimiter.blocked(blocked.untilEpochSeconds() - System.currentTimeMillis() / 1000);
        exceptionResolver.resolveException(request, response, null, limited);
    }

    /** 当前快照下生效的安检门配置：与真实路由重叠的蜜罐丢掉并告警 */
    private SecurityGateConfig gate() {
        CompiledSnapshot compiled = snapshotHolder.compiled();
        ActiveGate active = activeGate;
        if (active != null && active.source() == compiled) {
            return active.config();
        }
        SecurityGateConfig gate = compiled.gate();
        List<SecurityGateConfig.Honeypot> overlapping = handlerMapping == null ? List.of()
                : gate.overlapping(routePatterns());
        if (!overlapping.isEmpty()) {
            log.error("【告警】【蜜罐】新快照里的蜜罐路径与真实接口重叠，这几条不启用: {}", describe(overlapping));
            gate = gate.without(overlapping);
        }
        activeGate = new ActiveGate(compiled, gate);
        return gate;
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

    private static List<String> describe(List<SecurityGateConfig.Honeypot> honeypots) {
        return honeypots.stream().map(h -> h.code() + "=" + h.path()).toList();
    }

    /** 路径与查询串上的命中：别的网站能让访客的浏览器带着它来 */
    private static boolean urlBorne(String location) {
        return location.equals("path") || location.startsWith("query.");
    }

    /** 命中位置 → 表里的 matched_field */
    private static int matchedField(String location) {
        if (location.equals("ua")) {
            return SecurityIncidentEvent.FIELD_UA;
        }
        if (location.equals("path")) {
            return SecurityIncidentEvent.FIELD_PATH;
        }
        return location.startsWith("query.") ? SecurityIncidentEvent.FIELD_QUERY : SecurityIncidentEvent.FIELD_BODY;
    }

    /** 命中位置 → 参数 / 字段名（{@code query.id} → {@code id}，{@code body.items[1].name} → {@code items[1].name}） */
    private static String matchedParam(String location) {
        int dot = location.indexOf('.');
        return dot < 0 ? null : location.substring(dot + 1);
    }
}
