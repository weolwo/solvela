package solvela.member.risk.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.risk.config.RiskRuleRegistry;
import solvela.member.risk.rule.RiskRuleConfig;

import java.time.Duration;

/**
 * 登录相关的所有计数器。<b>Redis 键只在这里出现一次。</b>
 *
 * <h3>🔴 为什么键必须有唯一的归属</h3>
 * 每个计数器都有<b>两侧</b>：写的一侧在认证主干（失败时 +1），读的一侧在规则里（超没超）。
 * 键名或窗口在两侧各写一遍，漂移的第一天不会有任何报错 —— 表现是「这条限流再也不触发」，
 * 或者反过来「计数永不过期、限流再也不解除」。所以写、读、取 TTL 全部收在本类。
 *
 * <h3>窗口由规则的 params 说，不由这里说</h3>
 * 窗口是<b>写入时打的 TTL</b>，而阈值在规则里 —— 两者必须看同一份配置，否则后台把窗口从 1 小时改成 2 小时，
 * 规则按 2 小时判、计数却还是 1 小时就清零了。所以本类向规则表要窗口；
 * 规则缺失或关闭时退回 {@link Counter} 上的默认值（与改造前的硬编码值一致）。
 *
 * <p>注册那条计数器（{@link Counter#DEVICE_REGISTER}）不归任何规则 —— 注册还没进引擎（P3），
 * 它的窗口仍由 {@code DeviceGuardProperties} 说，所以这里只用默认值。
 *
 * <h3>固定窗口，不是滑动窗口</h3>
 * 沿用 {@code RedisService.increment} 的语义：第一次计数起算，TTL 到点整个计数清零。
 * 窗口边界前后各打满一次就能拿到两倍额度 —— 这是已知的取舍（方案 §4.3），换滑动窗口是单独一步（P3）。
 * 唯一的例外是 {@link Counter#DEVICE_MEMBER}：它每次 add 都续期，刻意的（见那里的注释）。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
public class LoginCounters {

    /**
     * 一个计数器：归谁管、键长什么样、窗口默认多长。
     *
     * @param ruleCode 窗口跟着这条规则的 params 走；null = 不归规则管，只用默认窗口
     * @param sliding  true = 每次写入都续期（只有一机多号是这样）
     */
    public enum Counter {

        /** 单 IP 的登录请求总数。在<b>规则里</b> +1（每次登录请求都算一次） */
        IP_ATTEMPT("IP_ATTEMPT_LIMIT", "login:ip:attempt:", Duration.ofHours(1), false),

        /** 单 IP 的登录失败数。只在<b>真的失败时</b> +1 */
        IP_FAIL("IP_FAIL_LIMIT", "login:ip:fail:", Duration.ofHours(1), false),

        /** 单 IP 尝试过的不存在账号数。查无此人时 +1，同时也算一次 {@link #IP_FAIL} */
        IP_UNKNOWN("IP_UNKNOWN_LIMIT", "login:ip:unknown:", Duration.ofHours(1), false),

        /** 单 IP 尝试过的<b>不同身份</b>（集合，存身份摘要）。不论账号存不存在都算 */
        IP_IDENTITY("IP_ACCOUNT_FANOUT", "login:ip:ids:", Duration.ofHours(1), false),

        /** 单设备的登录次数（含失败）。在规则里 +1 */
        DEVICE_LOGIN("DEVICE_LOGIN_LIMIT", "dev:login:", Duration.ofDays(1), false),

        /** 单设备的登录失败数。只在真的失败时 +1 */
        DEVICE_FAIL("DEVICE_FAIL_LIMIT", "dev:fail:", Duration.ofHours(1), false),

        /**
         * 单设备碰过的<b>不同会员</b>（集合）。
         *
         * <p>🔴 {@code sliding = true}，每次 add 都续期。只在首次创建时设 TTL 的话，
         * 一台持续活跃的设备会在第一次 add 的 24 小时后整个集合消失、计数归零 ——
         * <b>那正是养号要的效果</b>。
         */
        DEVICE_MEMBER("DEVICE_FANOUT", "dev:member:", Duration.ofDays(1), true),

        /** 单设备注册的账号数。注册还没进引擎，窗口由 DeviceGuardProperties 说 */
        DEVICE_REGISTER(null, "dev:reg:member:", Duration.ofDays(1), false),
        ;

        private final String ruleCode;

        private final String prefix;

        private final Duration defaultWindow;

        private final boolean sliding;

        Counter(String ruleCode, String prefix, Duration defaultWindow, boolean sliding) {
            this.ruleCode = ruleCode;
            this.prefix = prefix;
            this.defaultWindow = defaultWindow;
            this.sliding = sliding;
        }
    }

    private final RedisService redisService;

    private final StringRedisTemplate redis;

    /**
     * 🔴 用 {@link ObjectProvider} 延迟取，不在构造器里注入。
     *
     * <p>{@code RiskRuleRegistry} 的构造器会把所有 {@code RiskRule} bean 实例化出来做双向校验，
     * 而规则都依赖本类 —— 构造器直接注入 registry 就是一个循环依赖，Spring 会在启动时报错。
     */
    private final ObjectProvider<RiskRuleRegistry> registry;

    public LoginCounters(RedisService redisService, StringRedisTemplate redis,
                         ObjectProvider<RiskRuleRegistry> registry) {
        this.redisService = redisService;
        this.redis = redis;
        this.registry = registry;
    }

    /** 计数 +1，返回<b>含这一次</b>的累计值。维度值为空时返回 0（当作没数到） */
    public long bump(Counter counter, String value) {
        if (SolvelaStringUtil.isBlank(value)) {
            return 0L;
        }
        return redisService.increment(key(counter, value), windowSeconds(counter));
    }

    /**
     * 只读，不加。
     *
     * <p>🔴 失败类计数器<b>必须只读不加</b>：在判断那一步 +1 的话，每一次正常登录也被算成一次失败，
     * 阈值当场失去意义。
     */
    public long read(Counter counter, String value) {
        if (SolvelaStringUtil.isBlank(value)) {
            return 0L;
        }
        String raw = redisService.get(key(counter, value));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // 只可能是有人手改了 Redis。当成 0：一个读不出来的计数器不该让整个网段登不进来
            return 0L;
        }
    }

    /** 往集合里加一个成员，返回去重后的数量 */
    public long addToSet(Counter counter, String value, String member) {
        if (SolvelaStringUtil.isBlank(value) || SolvelaStringUtil.isBlank(member)) {
            return 0L;
        }
        String key = key(counter, value);
        redis.opsForSet().add(key, member);
        if (counter.sliding) {
            redis.expire(key, Duration.ofSeconds(windowSeconds(counter)));
        } else {
            // 固定窗口：只在刚创建时打 TTL。已存在却没有 TTL 的（历史坏键）也补上，让老数据自愈
            Long ttl = redis.getExpire(key);
            if (ttl == null || ttl < 0) {
                redis.expire(key, Duration.ofSeconds(windowSeconds(counter)));
            }
        }
        Long size = redis.opsForSet().size(key);
        return size == null ? 0L : size;
    }

    /** 这个计数器还要多久清零；至少 1 秒 —— 回「请 0 秒后重试」等于没说 */
    public long retryAfterSeconds(Counter counter, String value) {
        if (SolvelaStringUtil.isBlank(value)) {
            return 1L;
        }
        return Math.max(1L, redisService.getExpire(key(counter, value)));
    }

    public void clear(Counter counter, String value) {
        if (!SolvelaStringUtil.isBlank(value)) {
            redisService.delete(key(counter, value));
        }
    }

    private String key(Counter counter, String value) {
        return redisService.generateRedisKey(counter.prefix, value);
    }

    private long windowSeconds(Counter counter) {
        if (counter.ruleCode == null) {
            return counter.defaultWindow.toSeconds();
        }
        try {
            RiskRuleConfig config = registry.getObject().config(counter.ruleCode);
            if (config != null) {
                return RiskParams.duration(config.params(), "window", counter.defaultWindow).toSeconds();
            }
        } catch (RuntimeException e) {
            // 规则表取不到就用默认窗口。这里绝不能抛：它在写计数的路径上，而那条路径不该因为风控配置挂掉
            log.warn("【风控】取 {} 的窗口失败，用默认 {}", counter, counter.defaultWindow, e);
        }
        return counter.defaultWindow.toSeconds();
    }
}
