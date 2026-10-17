package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.Trace;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.RateLimitDimension;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.time.Instant;
import java.util.List;
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
 * 哪些维度跳过、封顶多久、阈值打几折，都由这个请求的 {@link Facts} 决定。
 *
 * <h3>🔴 Redis 出错就放行</h3>
 * 限流挂了不能等于整站挂了。每个维度各自容错：一个维度的脚本失败只跳过那一个维度。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
public class RateLimiter {

    /** 判的顺序：越准的维度越先判，一个维度说「挡」后面的就不再计数 */
    private static final List<RateLimitDimension> CHECK_ORDER = List.of(RateLimitDimension.DEVICE,
            RateLimitDimension.ACCOUNT, RateLimitDimension.IP, RateLimitDimension.IP_SEGMENT, RateLimitDimension.GLOBAL);

    /** {@code t_rate_limit_rule.mode}：1 = 陪跑（照常计数、只记录「本来会怎样」），2 = 生效；其余 = 停用 */
    private static final int MODE_SHADOW = 1, MODE_ENFORCE = 2;

    private final StringRedisTemplate redis;

    private final RuleSnapshotHolder snapshotHolder;

    private final RateLimitProperties properties;

    private final AccountKey accountKey;

    private final IncidentPublisher incidents;

    public RateLimiter(StringRedisTemplate redis, RuleSnapshotHolder snapshotHolder, RateLimitProperties properties,
                       IncidentPublisher incidents) {
        this.redis = redis;
        this.snapshotHolder = snapshotHolder;
        this.properties = properties;
        this.incidents = incidents;
        this.accountKey = new AccountKey(properties.accountKey());
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

        /** 匿名场景里的「账号」是请求体里别人填的目标，不是请求者自己 */
        boolean anonymousTarget() {
            return memberId == null && scene.anonymous();
        }
    }

    /**
     * 判一次；该挡就抛 {@code 429 OPERATION_LIMITED}（带 {@code retryAfterSeconds}，响应头的 {@code Retry-After} 由错误出口统一加）。
     */
    public void check(Request request) {
        if (!properties.enabled() || request.scene() == RateLimitScene.EXEMPT) {
            return;
        }
        CompiledSnapshot snapshot = snapshotHolder.compiled();
        Map<RateLimitDimension, GatewayRuleSnapshot.Rule> rules = snapshot.rulesFor(request.scene(), request.endpointKey());
        if (rules.isEmpty()) {
            return;
        }
        Facts facts = Facts.of(request, snapshot);
        for (RateLimitDimension dimension : CHECK_ORDER) {
            GatewayRuleSnapshot.Rule rule = rules.get(dimension);
            String value = active(rule) && !facts.skips(dimension) ? countedValue(dimension, request) : null;
            Outcome outcome = value == null ? null : run(rule, dimension, value, facts);
            if (outcome == null) {
                continue;
            }
            report(request, rule, dimension, outcome, facts.globals());
            if (outcome.blocked()) {
                throw blocked(outcome.retryAfter());
            }
        }
    }

    /** 对外的 429。安全闸挡下的请求也用它 —— 两边说的是同一句话，不告诉对方是哪一道闸 */
    static ApiException blocked(long retryAfter) {
        long seconds = Math.max(1, retryAfter);
        return new ApiException(ApiErrors.OPERATION_LIMITED, "操作过于频繁，请 " + humanize(seconds) + "后再试",
                Map.of("retryAfterSeconds", seconds));
    }

    // ================================================================== 一个请求的事实

    /**
     * 一个请求在判之前就定下来的事实：哪些维度跳过、封顶多久、阈值打几折。
     *
     * @param oldDevice       设备令牌签发满 {@code oldDeviceDays}
     * @param anonymousTarget 见 {@link Request#anonymousTarget()}
     */
    record Facts(RateLimitScene scene, long nowSeconds, boolean oldDevice, boolean whiteIp, boolean greyIp,
                 boolean greyMember, boolean anonymousTarget, RateLimitGlobals globals) {

        static Facts of(Request request, CompiledSnapshot snapshot) {
            long nowMillis = System.currentTimeMillis();
            long nowSeconds = nowMillis / 1000;
            IpRange ip = IpRange.single(request.clientIp());
            return new Facts(request.scene(), nowSeconds,
                    snapshot.oldDevice(request.deviceIssuedAt(), nowSeconds),
                    snapshot.ipLists().white(ip, nowMillis),
                    snapshot.ipLists().grey(ip, nowMillis),
                    snapshot.memberGrey(request.memberId(), nowMillis),
                    request.anonymousTarget(),
                    snapshot.globals());
        }

        /**
         * <ul>
         *   <li><b>老设备</b>、<b>IP 白名单</b>：跳过 IP 与 IP 段 —— 共用出口后面的真人都带着自己的老设备，
         *       而他们自己的设备维度照常在数（§2.3、§2.4）；</li>
         *   <li>老设备在匿名场景里也跳过账号维度（那里的「账号」是别人填的目标）；</li>
         *   <li>IP 段只用在匿名场景。</li>
         * </ul>
         */
        boolean skips(RateLimitDimension dimension) {
            if (dimension.ipBased() && (oldDevice || whiteIp)) {
                return true;
            }
            if (dimension == RateLimitDimension.ACCOUNT && anonymousTarget && oldDevice) {
                return true;
            }
            return dimension == RateLimitDimension.IP_SEGMENT && !scene.anonymous();
        }

        /** 处罚封顶秒数，0 = 不封顶。IP 段与匿名账号罚的可能是别人，封顶 30 分钟级 */
        long capSeconds(RateLimitDimension dimension) {
            if (dimension == RateLimitDimension.IP_SEGMENT) {
                return globals.ipSegmentCapSec();
            }
            return dimension == RateLimitDimension.ACCOUNT && anonymousTarget ? globals.anonAccountCapSec() : 0;
        }

        /** 灰名单打折：IP 灰名单作用在 IP 两个维度，会员灰名单作用在（登录后的）账号维度 */
        double factor(RateLimitDimension dimension) {
            boolean grey = dimension.ipBased() ? greyIp : dimension == RateLimitDimension.ACCOUNT && greyMember;
            return grey ? globals.greyFactor() : 1.0;
        }
    }

    // ================================================================== 一个维度

    /** 脚本返回的 kind（见 {@link RateLimitScript}），按编码顺序排列 */
    enum Verdict {
        /** 正常，或已在处罚中 */
        NONE,
        /** 本次刚判罚 / 升档 */
        PENALISED,
        /** 陪跑：本来会判罚 */
        SHADOW_PENALTY,
        /** 陪跑：本来会因突发拒绝 */
        SHADOW_BURST,
        /** 突发拒绝（第一次只拒不罚） */
        BURST_REJECTED;

        static Verdict of(long kind) {
            return kind >= 0 && kind < values().length ? values()[(int) kind] : NONE;
        }
    }

    /** 一个维度的结果 */
    record Outcome(boolean blocked, long retryAfter, int level, Verdict verdict) {
    }

    private static boolean active(GatewayRuleSnapshot.Rule rule) {
        return rule != null && (rule.mode() == MODE_SHADOW || rule.mode() == MODE_ENFORCE);
    }

    /** 该维度的计数值；没有可数的东西返回 null（跳过这个维度） */
    private String countedValue(RateLimitDimension dimension, Request request) {
        return switch (dimension) {
            case GLOBAL -> "*";
            case IP -> IpKeys.ip(request.clientIp());
            case IP_SEGMENT -> IpKeys.segment(request.clientIp());
            case DEVICE -> request.deviceId();
            case ACCOUNT -> request.memberId() != null ? "m:" + request.memberId()
                    : request.scene().anonymous() ? accountKey.of(request.rawAccount()) : null;
        };
    }

    private Outcome run(GatewayRuleSnapshot.Rule rule, RateLimitDimension dimension, String value, Facts facts) {
        try {
            List<?> r = redis.execute(RateLimitScript.SCRIPT,
                    List.of(SecurityKeys.rateLimitCounter(rule.target(), dimension.code(), value),
                            SecurityKeys.rateLimitPenalty(rule.target(), dimension.code(), value),
                            SecurityKeys.rateLimitLongCounter(rule.target(), dimension.code(), value)),
                    scriptArgs(rule, facts.nowSeconds(), facts.capSeconds(dimension), facts.factor(dimension),
                            facts.globals()));
            if (r == null || r.size() < 4) {
                return null;
            }
            return new Outcome(RateLimitScript.asLong(r.get(0)) == 1, RateLimitScript.asLong(r.get(1)),
                    (int) RateLimitScript.asLong(r.get(2)), Verdict.of(RateLimitScript.asLong(r.get(3))));
        } catch (Exception e) {
            log.error("【网关限流】脚本执行失败，放行这个维度, 规则: {}, 维度: {}", rule.target(), dimension, e);
            return null;
        }
    }

    /** 参数顺序见 {@link RateLimitScript} 的「参数」一节 */
    private static Object[] scriptArgs(GatewayRuleSnapshot.Rule rule, long now, long cap, double factor,
                                       RateLimitGlobals globals) {
        long longWindow = rule.longWindow() == null ? 0 : Math.max(0, RateLimitGlobals.seconds(rule.longWindow()));
        int longLimit = rule.limitLong() == null ? Integer.MAX_VALUE : scaled(rule.limitLong(), factor);
        return new Object[]{
                String.valueOf(now),
                rule.mode() == MODE_ENFORCE ? "1" : "0",
                String.valueOf(scaled(rule.burst(), factor)),
                String.valueOf(scaled(rule.limit3m(), factor)),
                String.valueOf(scaled(rule.limit5m(), factor)),
                String.valueOf(scaled(rule.limit10m(), factor)),
                String.valueOf(longWindow),
                String.valueOf(longLimit),
                globals.ladderSeconds().stream().map(String::valueOf).collect(Collectors.joining(",")),
                String.valueOf(globals.levelResetSeconds()),
                String.valueOf(cap)};
    }

    private void report(Request request, GatewayRuleSnapshot.Rule rule, RateLimitDimension dimension, Outcome outcome,
                        RateLimitGlobals globals) {
        switch (outcome.verdict()) {
            case PENALISED -> {
                log.warn("【网关限流】判罚, 规则: {}, 维度: {}, 第 {} 档, {} 秒, 接口: {}, IP: {}", rule.target(), dimension,
                        outcome.level(), outcome.retryAfter(), request.endpointKey(), request.clientIp());
                incidents.publish(incident(request, dimension, outcome, globals));
            }
            case SHADOW_PENALTY -> log.warn("【网关限流】陪跑命中（本来会判罚）, 规则: {}, 维度: {}, 接口: {}, IP: {}",
                    rule.target(), dimension, request.endpointKey(), request.clientIp());
            case SHADOW_BURST -> log.info("【网关限流】陪跑命中（本来会因突发拒绝）, 规则: {}, 维度: {}, 接口: {}",
                    rule.target(), dimension, request.endpointKey());
            case NONE, BURST_REJECTED -> {
            }
        }
    }

    /**
     * 一次判罚 / 升档的事件（只在状态变化时投：处罚期间被挡的请求不投）。罚到 {@code greyLevel} 档起：
     * <ul>
     *   <li>IP 维度 → 这个 IP 拉灰 {@code greyDays} 天；</li>
     *   <li>匿名场景的设备维度 → 这台设备进观察档（§5.5）；</li>
     *   <li>登录后场景的账号维度 → 这个会员拉灰 {@code greyDays} 天。
     *       🔴 匿名场景的「账号」是请求体里别人填的目标，永远不拉灰 —— 否则就是一个拿别人的号去拉灰的办法（§2.4）。</li>
     * </ul>
     */
    private static SecurityIncidentEvent incident(Request request, RateLimitDimension dimension, Outcome outcome,
                                                  RateLimitGlobals globals) {
        long now = Instant.now().getEpochSecond();
        long greyUntil = now + globals.greyDays() * 86400L;
        boolean high = outcome.level() >= globals.greyLevel();
        SecurityIncidentEvent.Builder event = SecurityIncidentEvent
                .builder(SecurityIncidentEvent.RATE_LIMIT, request.scene().name())
                .penalty(outcome.level(), now + outcome.retryAfter())
                .who(request.clientIp(), request.deviceId(), request.memberId())
                .request(method(request.endpointKey()), path(request.endpointKey()), null, Trace.id())
                .matched(null, dimension.name(), null)
                .observeDevice(high && dimension == RateLimitDimension.DEVICE && request.scene().anonymous());
        if (high && dimension == RateLimitDimension.IP) {
            event.action(SecurityIncidentEvent.ACTION_GREY, greyUntil);
        }
        if (high && dimension == RateLimitDimension.ACCOUNT && request.memberId() != null && !request.scene().anonymous()) {
            event.greyMember(greyUntil);
        }
        return event.build();
    }

    // ================================================================== 工具

    /** {@code METHOD 模板路径} 的前半段 */
    private static String method(String endpointKey) {
        int space = endpointKey == null ? -1 : endpointKey.indexOf(' ');
        return space < 0 ? null : endpointKey.substring(0, space);
    }

    /** {@code METHOD 模板路径} 的后半段 */
    private static String path(String endpointKey) {
        int space = endpointKey == null ? -1 : endpointKey.indexOf(' ');
        return space < 0 ? endpointKey : endpointKey.substring(space + 1);
    }

    /** 灰名单打折后的阈值，至少 1 */
    private static int scaled(int limit, double factor) {
        return factor >= 1.0 ? limit : Math.max(1, (int) Math.ceil(limit * factor));
    }

    /** 两分钟内按秒、两小时内按分钟、再长按小时（都向上取整） */
    private static String humanize(long seconds) {
        if (seconds < 120) {
            return seconds + " 秒";
        }
        return seconds < 7200 ? (seconds + 59) / 60 + " 分钟" : (seconds + 3599) / 3600 + " 小时";
    }
}
