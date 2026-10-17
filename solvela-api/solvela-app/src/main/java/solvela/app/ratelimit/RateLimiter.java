package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网关限流的判定（docs/架构/网关阶梯限流-技术方案.md §2–§4）。
 *
 * <h3>一次请求怎么判</h3>
 * <ol>
 *   <li>生效规则 = 场景的行 ∪ 接口的行；同一维度上接口行覆盖场景行，并且<b>单独计数</b>；</li>
 *   <li>按「设备 → 账号 → IP → IP 段 → 全局」逐个维度调一次脚本（越准的维度越先判）；</li>
 *   <li>任何一个维度说「挡」就停：后面的维度不再计数 —— 被挡的请求不该再去推高别的计数器。</li>
 * </ol>
 *
 * <h3>哪些维度会被跳过</h3>
 * <ul>
 *   <li><b>老设备</b>（令牌签发早于 {@code oldDeviceDays}）：跳过 IP、IP 段，以及匿名场景里的账号维度。
 *       共用出口后面的真人都带着自己的老设备，而他们自己的设备维度照常在数（§2.3、§2.4）；</li>
 *   <li><b>IP 白名单</b>：跳过 IP、IP 段；</li>
 *   <li>IP 段只用在匿名场景，非匿名场景的 IP 段行忽略。</li>
 * </ul>
 *
 * <h3>🔴 Redis 出错就放行</h3>
 * 限流挂了不能等于整站挂了。每个维度各自容错：一个维度的脚本失败只跳过那一个维度。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
public class RateLimiter {

    /** 维度编码，与 {@code t_rate_limit_rule.dimension} 一致 */
    static final int GLOBAL = 0, IP = 1, ACCOUNT = 2, DEVICE = 3, IP_SEGMENT = 4;

    private static final int[] ORDER = {DEVICE, ACCOUNT, IP, IP_SEGMENT, GLOBAL};

    private static final int MODE_SHADOW = 1, MODE_ENFORCE = 2;

    private final StringRedisTemplate redis;

    private final RuleSnapshotHolder snapshotHolder;

    private final RateLimitProperties properties;

    private final SecretKeySpec accountKey;

    private final IncidentPublisher incidents;

    /** 由快照算出来的东西，换快照时才重算 */
    private volatile Derived derived;

    public RateLimiter(StringRedisTemplate redis, RuleSnapshotHolder snapshotHolder, RateLimitProperties properties,
                       IncidentPublisher incidents) {
        this.redis = redis;
        this.snapshotHolder = snapshotHolder;
        this.properties = properties;
        this.incidents = incidents;
        this.accountKey = properties.accountKey() == null ? null
                : new SecretKeySpec(properties.accountKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * 一次请求要判的东西。
     *
     * @param endpointKey    {@code METHOD 模板路径}，用来找接口覆盖行
     * @param deviceIssuedAt 设备令牌签发时间；没有设备为 null
     * @param memberId       已登录的会员号；匿名为 null
     * @param rawAccount     匿名场景请求体里的目标（邮箱 / 手机号）；没有为 null
     */
    public record Request(RateLimitScene scene, String endpointKey, String clientIp, String deviceId,
                          Instant deviceIssuedAt, Long memberId, String rawAccount) {
    }

    /**
     * 判一次；该挡就抛 {@code 429 OPERATION_LIMITED}（带 {@code retryAfterSeconds}，响应头的 {@code Retry-After} 由错误出口统一加）。
     */
    public void check(Request request) {
        if (!properties.enabled() || request.scene() == RateLimitScene.EXEMPT) {
            return;
        }
        Derived d = derived();
        Map<Integer, GatewayRuleSnapshot.Rule> rules = effectiveRules(d, request);
        if (rules.isEmpty()) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        boolean oldDevice = request.deviceIssuedAt() != null
                && now - request.deviceIssuedAt().getEpochSecond() >= d.globals.oldDeviceSeconds();
        IpRange ip = IpRange.single(request.clientIp());
        long nowMillis = System.currentTimeMillis();
        boolean white = d.lists.white(ip, nowMillis);
        boolean grey = d.lists.grey(ip, nowMillis);
        boolean anonymousTarget = request.memberId() == null && request.scene().anonymous();

        for (int dim : ORDER) {
            GatewayRuleSnapshot.Rule rule = rules.get(dim);
            if (rule == null || (rule.mode() != MODE_SHADOW && rule.mode() != MODE_ENFORCE)) {
                continue;
            }
            boolean ipDim = dim == IP || dim == IP_SEGMENT;
            if ((ipDim && (oldDevice || white)) || (dim == ACCOUNT && anonymousTarget && oldDevice)) {
                continue;
            }
            if (dim == IP_SEGMENT && !request.scene().anonymous()) {
                continue;
            }
            String value = value(dim, request);
            if (value == null) {
                continue;
            }
            long cap = dim == IP_SEGMENT ? d.globals.ipSegmentCapSec()
                    : dim == ACCOUNT && anonymousTarget ? d.globals.anonAccountCapSec() : 0;
            double factor = ipDim && grey ? d.globals.greyFactor() : 1.0;
            Outcome outcome = run(rule, dim, value, now, cap, factor, d.globals);
            if (outcome == null) {
                continue;
            }
            report(request, rule, dim, outcome, d.globals);
            if (outcome.blocked()) {
                throw blocked(outcome.retryAfter());
            }
        }
    }

    /** 一个维度的结果 */
    record Outcome(boolean blocked, long retryAfter, int level, int kind) {
    }

    private Outcome run(GatewayRuleSnapshot.Rule rule, int dim, String value, long now, long cap,
                        double factor, RateLimitGlobals globals) {
        long longWindow = rule.longWindow() == null ? 0 : Math.max(0, RateLimitGlobals.seconds(rule.longWindow()));
        try {
            List<?> r = redis.execute(RateLimitScript.SCRIPT,
                    List.of(SecurityKeys.rateLimitCounter(rule.target(), dim, value),
                            SecurityKeys.rateLimitPenalty(rule.target(), dim, value),
                            SecurityKeys.rateLimitLongCounter(rule.target(), dim, value)),
                    String.valueOf(now),
                    rule.mode() == MODE_ENFORCE ? "1" : "0",
                    String.valueOf(scaled(rule.burst(), factor)),
                    String.valueOf(scaled(rule.limit3m(), factor)),
                    String.valueOf(scaled(rule.limit5m(), factor)),
                    String.valueOf(scaled(rule.limit10m(), factor)),
                    String.valueOf(longWindow),
                    String.valueOf(rule.limitLong() == null ? Integer.MAX_VALUE : scaled(rule.limitLong(), factor)),
                    globals.ladderSeconds().stream().map(String::valueOf).collect(Collectors.joining(",")),
                    String.valueOf(globals.levelResetSeconds()),
                    String.valueOf(cap));
            if (r == null || r.size() < 4) {
                return null;
            }
            return new Outcome(toLong(r.get(0)) == 1, toLong(r.get(1)), (int) toLong(r.get(2)), (int) toLong(r.get(3)));
        } catch (Exception e) {
            log.error("【网关限流】脚本执行失败，放行这个维度, 规则: {}, 维度: {}", rule.target(), dim, e);
            return null;
        }
    }

    private void report(Request request, GatewayRuleSnapshot.Rule rule, int dim, Outcome outcome,
                        RateLimitGlobals globals) {
        switch (outcome.kind()) {
            case 1 -> {
                log.warn("【网关限流】判罚, 规则: {}, 维度: {}, 第 {} 档, {} 秒, 接口: {}, IP: {}",
                        rule.target(), dim, outcome.level(), outcome.retryAfter(), request.endpointKey(), request.clientIp());
                incidents.publish(incident(request, dim, outcome, globals));
            }
            case 2 -> log.warn("【网关限流】陪跑命中（本来会判罚）, 规则: {}, 维度: {}, 接口: {}, IP: {}",
                    rule.target(), dim, request.endpointKey(), request.clientIp());
            case 3 -> log.info("【网关限流】陪跑命中（本来会因突发拒绝）, 规则: {}, 维度: {}, 接口: {}",
                    rule.target(), dim, request.endpointKey());
            default -> {
            }
        }
    }

    /**
     * 一次判罚 / 升档的事件（只在状态变化时投：处罚期间被挡的请求不投）。罚到 {@code greyLevel} 档起：
     * IP 维度 → 这个 IP 拉灰 {@code greyDays} 天；匿名场景的设备维度 → 这台设备进观察档（§5.5）。
     * 账号维度只记录：登录后场景的会员拉灰在会员灰名单那一步（T12），匿名场景的「账号」是别人填的目标，永远不拉灰。
     */
    private static SecurityIncidentEvent incident(Request request, int dim, Outcome outcome, RateLimitGlobals globals) {
        long now = Instant.now().getEpochSecond();
        boolean high = outcome.level() >= globals.greyLevel();
        SecurityIncidentEvent.Builder event = SecurityIncidentEvent
                .builder(SecurityIncidentEvent.RATE_LIMIT, request.scene().name())
                .penalty(outcome.level(), now + outcome.retryAfter())
                .who(request.clientIp(), request.deviceId(), request.memberId())
                .request(method(request.endpointKey()), path(request.endpointKey()), null, solvela.app.web.Trace.id())
                .matched(null, dimensionName(dim), null)
                .observeDevice(high && dim == DEVICE && request.scene().anonymous());
        if (high && dim == IP) {
            event.action(SecurityIncidentEvent.ACTION_GREY, now + globals.greyDays() * 86400L);
        }
        return event.build();
    }

    private static String dimensionName(int dim) {
        return switch (dim) {
            case IP -> "IP";
            case ACCOUNT -> "ACCOUNT";
            case DEVICE -> "DEVICE";
            case IP_SEGMENT -> "IP_SEGMENT";
            default -> "GLOBAL";
        };
    }

    private static String method(String endpointKey) {
        int space = endpointKey == null ? -1 : endpointKey.indexOf(' ');
        return space < 0 ? null : endpointKey.substring(0, space);
    }

    private static String path(String endpointKey) {
        int space = endpointKey == null ? -1 : endpointKey.indexOf(' ');
        return space < 0 ? endpointKey : endpointKey.substring(space + 1);
    }

    /** 该维度的计数值；没有可数的东西返回 null（跳过这个维度） */
    private String value(int dim, Request request) {
        return switch (dim) {
            case GLOBAL -> "*";
            case IP -> IpKeys.ip(request.clientIp());
            case IP_SEGMENT -> IpKeys.segment(request.clientIp());
            case DEVICE -> request.deviceId();
            case ACCOUNT -> request.memberId() != null ? "m:" + request.memberId() : anonymousAccount(request);
            default -> null;
        };
    }

    /** 匿名场景的目标：先按会员域的规则规范化（大小写不同的邮箱必须是同一个账号），再算 HMAC */
    private String anonymousAccount(Request request) {
        if (!request.scene().anonymous() || request.rawAccount() == null || request.rawAccount().isBlank()
                || accountKey == null) {
            return null;
        }
        String raw = request.rawAccount();
        String normalized = raw.contains("@") ? MemberEmailUtil.normalize(raw) : MemberPhoneUtil.normalize(raw);
        if (normalized == null) {
            // 格式不对的输入会被会员域拒绝；这里照样数，免得「故意填错格式」成了绕开账号维度的办法
            normalized = raw.strip().toLowerCase(Locale.ROOT);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(accountKey);
            return "a:" + HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (Exception e) {
            log.error("【网关限流】账号键计算失败，跳过账号维度", e);
            return null;
        }
    }

    /** 对外的 429。安全闸挡下的请求也用它 —— 两边说的是同一句话，不告诉对方是哪一道闸 */
    static ApiException blocked(long retryAfter) {
        long seconds = Math.max(1, retryAfter);
        String wait = seconds < 120 ? seconds + " 秒" : seconds < 7200 ? ((seconds + 59) / 60) + " 分钟" : ((seconds + 3599) / 3600) + " 小时";
        return new ApiException(ApiErrors.OPERATION_LIMITED, "操作过于频繁，请 " + wait + "后再试",
                Map.of("retryAfterSeconds", seconds));
    }

    /** 场景行打底，接口行覆盖同一维度 */
    private static Map<Integer, GatewayRuleSnapshot.Rule> effectiveRules(Derived d, Request request) {
        Map<Integer, GatewayRuleSnapshot.Rule> result = new HashMap<>();
        Map<Integer, GatewayRuleSnapshot.Rule> scene = d.sceneRules.get(request.scene().name());
        if (scene != null) {
            result.putAll(scene);
        }
        Map<Integer, GatewayRuleSnapshot.Rule> endpoint =
                request.endpointKey() == null ? null : d.endpointRules.get(request.endpointKey());
        if (endpoint != null) {
            result.putAll(endpoint);
        }
        return result;
    }

    private static int scaled(int limit, double factor) {
        return factor >= 1.0 ? limit : Math.max(1, (int) Math.ceil(limit * factor));
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
    }

    private Derived derived() {
        GatewayRuleSnapshot snapshot = snapshotHolder.current();
        Derived d = derived;
        if (d == null || d.snapshot != snapshot) {
            d = new Derived(snapshot);
            derived = d;
        }
        return d;
    }

    /** 从一份快照算出来的查找表。只在快照换了的时候重算一次 */
    static final class Derived {

        final GatewayRuleSnapshot snapshot;

        final Map<String, Map<Integer, GatewayRuleSnapshot.Rule>> sceneRules = new HashMap<>();

        final Map<String, Map<Integer, GatewayRuleSnapshot.Rule>> endpointRules = new HashMap<>();

        final IpLists lists;

        final RateLimitGlobals globals;

        Derived(GatewayRuleSnapshot snapshot) {
            this.snapshot = snapshot;
            for (GatewayRuleSnapshot.Rule rule : snapshot.rules()) {
                Map<String, Map<Integer, GatewayRuleSnapshot.Rule>> target =
                        rule.targetType() == 2 ? endpointRules : sceneRules;
                target.computeIfAbsent(rule.target(), k -> new HashMap<>()).put(rule.dimension(), rule);
            }
            this.lists = new IpLists(snapshot);
            this.globals = RateLimitGlobals.parse(snapshot.configs() == null ? null
                    : snapshot.configs().get("gateway_rate_limit"));
        }
    }
}
