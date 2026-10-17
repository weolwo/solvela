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
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import solvela.app.auth.CurrentDevice;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.ClientIp;
import solvela.auth.device.DeviceIdentity;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.KeywordRules;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;
import solvela.security.SlidingWindowScripts;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全闸（网关方案 §5.5）：① 查封禁 → ② 蜜罐路径 → ③ 关键字。越便宜的检查越往前。
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
 * <h2>③ 关键字：参数里带着 {@code sleep(5)}、{@code union select} 的请求一看就不是正常用户</h2>
 * 规则与反例集在 {@link KeywordRules}（后台保存时用同一份校验）。命中 → 罚法与蜜罐相同；对外与参数校验失败<b>逐字相同</b>
 * （{@code 400 INVALID_ARGUMENT}），不告诉对方「你被拦了」。路径与查询串上的跨站命中照样拒绝这一次、但不罚；
 * 每条规则各自熔断，熔断时<b>放行</b>（那时挡下的多半是真人）。
 * 扫过的 JSON 请求体换成 {@link CachedBodyRequest} 往下传，Controller 照样能绑定参数。
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

    /** 只用来读请求体的树，不塑造任何输出 */
    private static final JsonMapper BODY_MAPPER = JsonMapper.builder().build();

    /** 熔断集合的成员上限：内存的硬上限，远大于任何合理的阈值 */
    private static final int BREAKER_MAX_IPS = 10_000;

    /** 熔断告警的最小间隔：熔断期间每次命中都打一条 ERROR 等于没有 ERROR */
    private static final long BREAKER_ALARM_INTERVAL_MILLIS = 60_000;

    private final StringRedisTemplate redis;

    private final RuleSnapshotHolder snapshotHolder;

    private final HandlerExceptionResolver exceptionResolver;

    /** 真实路由的来源；为 null 时不做重叠检查（只在测试里） */
    private final RequestMappingHandlerMapping handlerMapping;

    /** 熔断键的前缀：生产为空串；测试换一个，免得碰到本机网关真在用的熔断计数 */
    private final String breakerNamespace;

    private final IncidentPublisher incidents;

    /** 被挡次数；为 null 时不计（只在测试里） */
    private final BlockedCounter blockedCounter;

    private final PenaltyCache cache = new PenaltyCache();

    /** 每个熔断键上一次告警的时刻 */
    private final ConcurrentHashMap<String, Long> lastBreakerAlarm = new ConcurrentHashMap<>();

    private volatile Derived derived;

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
        this.redis = redis;
        this.snapshotHolder = snapshotHolder;
        this.exceptionResolver = exceptionResolver;
        this.handlerMapping = handlerMapping;
        this.breakerNamespace = breakerNamespace;
        this.incidents = incidents;
        this.blockedCounter = blockedCounter;
    }

    /** 一个请求里安全闸要用到的东西。蜜罐与关键字的判定、事件都从这里取 */
    record Probe(String method, String path, String clientIp, DeviceIdentity device, String secFetchSite,
                 String origin, String userAgent, String traceId) {

        String deviceId() {
            return device == null ? null : device.deviceId();
        }
    }

    /** 快照换了才重算 */
    private record Derived(GatewayRuleSnapshot snapshot, IpLists lists, long oldDeviceSeconds,
                           SecurityGateConfig gate) {
    }

    /** 一次蜜罐 / 关键字检查的结果 */
    enum Trap { PENALISED, CROSS_SITE, BREAKER_OPEN, MISS }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = ClientIp.of(request);
        DeviceIdentity device = CurrentDevice.find().orElse(null);
        PenaltyCache.Blocked blocked = check(ip, device);
        if (blocked != null) {
            if (blockedCounter != null) {
                blockedCounter.record(blocked.listValue());
            }
            exceptionResolver.resolveException(request, response, null, limited(blocked));
            return;
        }
        Probe probe = new Probe(request.getMethod(), request.getRequestURI(), ip, device,
                request.getHeader("Sec-Fetch-Site"), request.getHeader("Origin"), request.getHeader("User-Agent"),
                solvela.app.web.Trace.id());
        if (trap(probe) != Trap.MISS) {
            // 蜜罐照常往下走：落到「找不到」，与任何不存在的路径同一个响应。关键字不用再扫了
            chain.doFilter(request, response);
            return;
        }

        byte[] body = smallJsonBody(request);
        HttpServletRequest forward = body == null ? request : new CachedBodyRequest(request, body);
        KeywordRules.Hit hit = scan(derived().gate().keywords(), request.getRequestURI(), request.getQueryString(),
                body, probe.userAgent());
        if (hit != null && keyword(hit, probe) != Trap.BREAKER_OPEN) {
            // 与参数校验失败逐字相同，不告诉对方「你被拦了」；之后的请求才开始收到 429
            exceptionResolver.resolveException(request, response, null, new ApiException(ApiErrors.INVALID_ARGUMENT));
            return;
        }
        chain.doFilter(forward, response);
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
            return new PenaltyCache.Blocked(black.expireAtMillis() == null ? 0 : black.expireAtMillis() / 1000,
                    black.value());
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
            return remember(ipCacheKey, new PenaltyCache.Blocked(ipUntil, IpKeys.listValue(clientIp)), nowMillis,
                    "IP 处罚中", ipKey);
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
        return trap(new Probe("GET", requestPath, clientIp, device, secFetchSite, origin, null, null));
    }

    Trap trap(Probe probe) {
        Derived d = derived();
        SecurityGateConfig.Honeypot honeypot = d.gate().honeypot(probe.path());
        if (honeypot == null) {
            return Trap.MISS;
        }
        SecurityIncidentEvent.Builder event = SecurityIncidentEvent.builder(SecurityIncidentEvent.HONEYPOT, honeypot.code())
                .matched(SecurityIncidentEvent.FIELD_PATH, null, probe.path());
        if (!sameOriginOrNonBrowser(probe.secFetchSite(), probe.origin())) {
            log.warn("【蜜罐】跨站发起的命中，只记录不处罚, 蜜罐: {}, 路径: {}, IP: {}, Sec-Fetch-Site: {}, Origin: {}",
                    honeypot.code(), probe.path(), probe.clientIp(), probe.secFetchSite(), probe.origin());
            publish(event.suppressed(SecurityIncidentEvent.SUPPRESS_CROSS_SITE), probe);
            return Trap.CROSS_SITE;
        }
        return penalise("蜜罐", honeypot.code(), "路径 " + probe.path(), breakerNamespace + SecurityKeys.HONEYPOT_BREAKER,
                probe, event, d);
    }

    // ================================================================== ③ 关键字

    /**
     * 扫一遍请求（网关方案 §5.4）：路径与查询串先 URL 解码一次，JSON 请求体按字段扫值，{@code User-Agent} 只给 UA 规则看。
     * 🔴 密码、验证码一类字段（{@link KeywordRules#isSecretField}）不参与匹配。
     *
     * @param body 已读出的 JSON 请求体；不扫请求体为 null
     * @return 第一处命中；没命中为 null
     */
    static KeywordRules.Hit scan(List<KeywordRules.Rule> rules, String requestUri, String queryString, byte[] body,
                                 String userAgent) {
        if (rules.isEmpty()) {
            return null;
        }
        KeywordRules.Hit hit = KeywordRules.find(rules, KeywordRules.Scope.UA, userAgent, "ua");
        if (hit == null) {
            hit = KeywordRules.find(rules, KeywordRules.Scope.PARAM, decodePath(requestUri), "path");
        }
        if (hit == null && queryString != null) {
            for (String pair : queryString.split("&")) {
                int eq = pair.indexOf('=');
                String name = decodeQuery(eq < 0 ? pair : pair.substring(0, eq));
                if (eq < 0 || KeywordRules.isSecretField(name)) {
                    continue;
                }
                hit = KeywordRules.find(rules, KeywordRules.Scope.PARAM, decodeQuery(pair.substring(eq + 1)), "query." + name);
                if (hit != null) {
                    break;
                }
            }
        }
        if (hit == null && body != null) {
            JsonNode root;
            try {
                root = BODY_MAPPER.readTree(body);
            } catch (Exception e) {
                // 解不开的 JSON 由参数绑定回 400，这里不必再看
                return null;
            }
            hit = scanJson(rules, root, "body");
        }
        return hit;
    }

    private static KeywordRules.Hit scanJson(List<KeywordRules.Rule> rules, JsonNode node, String location) {
        if (node == null) {
            return null;
        }
        if (node.isString()) {
            return KeywordRules.find(rules, KeywordRules.Scope.PARAM, node.asString(), location);
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                KeywordRules.Hit hit = scanJson(rules, node.get(i), location + "[" + i + "]");
                if (hit != null) {
                    return hit;
                }
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (KeywordRules.isSecretField(field.getKey())) {
                    continue;
                }
                KeywordRules.Hit hit = scanJson(rules, field.getValue(), location + "." + field.getKey());
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * 命中一条关键字之后罚不罚。路径与查询串上的跨站命中不罚（一个图片标签就能让访客的浏览器带着它来），
     * 请求体与 UA 不看这一条：JSON 请求体从别的网站发不过来（要过 CORS 预检），UA 浏览器里改不了。
     *
     * <p>🔴 熔断时<b>放行</b>这个请求：很多不同 IP 同时命中同一条规则，更可能是规则写宽了或前端在发这类字符 ——
     * 那时挡下的是真人。
     */
    Trap keyword(KeywordRules.Hit hit, String clientIp, DeviceIdentity device, String secFetchSite, String origin) {
        return keyword(hit, new Probe("GET", "/", clientIp, device, secFetchSite, origin, null, null));
    }

    Trap keyword(KeywordRules.Hit hit, Probe probe) {
        boolean urlBorne = hit.location().equals("path") || hit.location().startsWith("query.");
        SecurityIncidentEvent.Builder event = SecurityIncidentEvent.builder(SecurityIncidentEvent.KEYWORD, hit.code())
                .matched(matchedField(hit.location()), matchedParam(hit.location()), hit.sample());
        if (urlBorne && !sameOriginOrNonBrowser(probe.secFetchSite(), probe.origin())) {
            log.warn("【关键字】跨站发起的命中，拒绝但不处罚, 规则: {}, 位置: {}, 片段: {}, IP: {}",
                    hit.code(), hit.location(), hit.sample(), probe.clientIp());
            publish(event.suppressed(SecurityIncidentEvent.SUPPRESS_CROSS_SITE), probe);
            return Trap.CROSS_SITE;
        }
        return penalise("关键字", hit.code(), hit.location() + " 「" + hit.sample() + "」",
                breakerNamespace + SecurityKeys.keywordBreaker(hit.code()), probe, event, derived());
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

    // ================================================================== 蜜罐与关键字共用的处罚

    /**
     * IP 与设备各罚 {@code ban}（作用于所有接口），写进本实例缓存（下一个请求就挡，不用去 Redis），并投一条事件
     * （IP 被罚的，落库任务把它拉黑到同一时刻）。
     * <ul>
     *   <li>先过熔断：窗口内太多不同 IP 命中同一个来源 → 不罚、告警；</li>
     *   <li>🔴 命中的是<b>老设备</b>：只罚设备，IP 那一份压下（§5.2）—— 共用出口后面的真人不该因为别人而被封一天。
     *       设备那一份照罚：设备维度是它自己的账。</li>
     * </ul>
     */
    private Trap penalise(String source, String code, String detail, String breakerKey, Probe probe,
                          SecurityIncidentEvent.Builder event, Derived d) {
        String clientIp = probe.clientIp();
        String deviceId = probe.deviceId();
        String ipKey = IpRange.single(clientIp) == null ? null : IpKeys.ip(clientIp);
        long nowMillis = System.currentTimeMillis();
        if (ipKey != null && breakerOpen(breakerKey, ipKey, nowMillis, d.gate(), source + " " + code)) {
            log.warn("【{}】熔断中，只记录不处罚, 规则: {}, {}, IP: {}", source, code, detail, clientIp);
            publish(event.suppressed(SecurityIncidentEvent.SUPPRESS_BREAKER), probe);
            return Trap.BREAKER_OPEN;
        }
        long ban = d.gate().banSeconds();
        long until = nowMillis / 1000 + ban;
        if (ipKey != null && isOld(probe.device(), nowMillis / 1000, d)) {
            ipKey = null;
            event.suppressed(SecurityIncidentEvent.SUPPRESS_OLD_DEVICE);
        }
        event.penalty(null, until);
        if (ipKey != null) {
            event.action(SecurityIncidentEvent.ACTION_BLACK, until);
        }
        try {
            if (ipKey != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyIp(ipKey), String.valueOf(until), Duration.ofSeconds(ban));
            }
            if (deviceId != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyDevice(deviceId), String.valueOf(until),
                        Duration.ofSeconds(ban));
            }
        } catch (Exception e) {
            log.error("【{}】处罚写不进 Redis，这次只记录, 规则: {}, IP: {}", source, code, clientIp, e);
            publish(event, probe);
            return Trap.PENALISED;
        }
        if (ipKey != null) {
            cache.put(ipCacheKey(ipKey), new PenaltyCache.Blocked(until, IpKeys.listValue(clientIp)), nowMillis);
        }
        if (deviceId != null) {
            cache.put(deviceCacheKey(deviceId), new PenaltyCache.Blocked(until), nowMillis);
        }
        publish(event, probe);
        if (d.lists().white(IpRange.single(clientIp), nowMillis)) {
            log.error("【告警】【{}】白名单 IP 命中（照样处罚，白名单只豁免限流）, 规则: {}, {}, IP: {}, 设备: {}",
                    source, code, detail, clientIp, deviceId);
        } else {
            log.warn("【{}】命中，IP 与设备处罚 {} 秒, 规则: {}, {}, IP: {}, 设备: {}",
                    source, ban, code, detail, clientIp, deviceId);
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

    private void publish(SecurityIncidentEvent.Builder event, Probe probe) {
        if (incidents == null) {
            return;
        }
        incidents.publish(event.who(probe.clientIp(), probe.deviceId(), null)
                .request(probe.method(), probe.path(), probe.userAgent(), probe.traceId())
                .build());
    }

    /** 第 2 道保险：把这个 IP 记进熔断窗口，返回「是否已经熔断」 */
    private boolean breakerOpen(String breakerKey, String ipKey, long nowMillis, SecurityGateConfig gate, String what) {
        Long distinct;
        try {
            distinct = redis.execute(DISTINCT, List.of(breakerKey), ipKey,
                    String.valueOf(gate.breakerWindowSeconds() * 1000), String.valueOf(nowMillis),
                    String.valueOf(BREAKER_MAX_IPS));
        } catch (Exception e) {
            // 数不了就不知道是不是在集中命中 —— 照常处罚：会碰到这些的本来就只有扫描器
            log.error("【安全闸】熔断计数失败，按未熔断处理, {}", what, e);
            return false;
        }
        if (distinct == null || distinct <= gate.breakerDistinctIps()) {
            return false;
        }
        Long previous = lastBreakerAlarm.get(breakerKey);
        if (previous == null || nowMillis - previous >= BREAKER_ALARM_INTERVAL_MILLIS) {
            lastBreakerAlarm.put(breakerKey, nowMillis);
            log.error("【告警】【安全闸】熔断：{}，{} 秒内 {} 个不同 IP 命中（阈值 {}），停止处罚。"
                            + "更可能是我们自己出了问题：前端在请求蜜罐路径 / 发这类字符，规则写宽了，或蜜罐路径被公开链接了",
                    what, gate.breakerWindowSeconds(), distinct, gate.breakerDistinctIps());
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

    /**
     * 要扫的 JSON 请求体：有请求体的方法、{@code Content-Type} 是 JSON、声明了长度且不超过 {@link KeywordRules#MAX_BODY_BYTES}。
     * 不满足的返回 null（不扫）—— 分块传输、超大的、文件上传与表单都不扫，成本不成比例。
     */
    private static byte[] smallJsonBody(HttpServletRequest request) throws IOException {
        String method = request.getMethod();
        String type = request.getContentType();
        long length = request.getContentLengthLong();
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))
                || type == null || !type.toLowerCase(Locale.ROOT).contains("json")
                || length <= 0 || length > KeywordRules.MAX_BODY_BYTES) {
            return null;
        }
        return request.getInputStream().readNBytes((int) length);
    }

    /** 路径解码一次；坏的百分号编码按原样扫 */
    private static String decodePath(String path) {
        try {
            return UriUtils.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return path;
        }
    }

    /** 查询串里的一段解码一次（{@code +} 是空格）；坏的百分号编码按原样扫 */
    private static String decodeQuery(String part) {
        try {
            return URLDecoder.decode(part, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return part;
        }
    }

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
