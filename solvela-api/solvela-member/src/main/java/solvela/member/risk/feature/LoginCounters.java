package solvela.member.risk.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 * <h3>滑动窗口（2026-10-16 从固定窗口换过来）</h3>
 * 固定窗口的毛病是<b>边界加倍</b>：窗口快结束时打满一次、跨过边界再打满一次，
 * 实际两分钟内拿到两倍额度，而每个窗口的计数都「没超」—— 而这几条限流正好是
 * 攻击者有动机去卡这个边界的地方（方案 §4.3）。
 *
 * <p>现在走 {@code RedisService.slidingWindowCount}（计数，分桶）与
 * {@code slidingSetCount}（去重计数，ZSET）。两种都是一次往返、原子完成。
 *
 * <p>🔴 <b>本类对外的方法一个字没变</b>，所以规则一条都没改 —— 这正是把 Redis 键
 * 收在一处的回报：换窗口语义这件事，影响面停在这个类里面。
 *
 * <p>一个副作用是 {@link Counter#DEVICE_MEMBER} 原来那个「每次 add 都续期」的特例没了：
 * 续期是为了防「整个集合在首次 add 的 24 小时后消失、计数归零」，而滑动窗口里
 * 成员是<b>各自</b>到点掉出去的 —— 那比两种旧写法都更接近「最近 24 小时碰过几个会员」。
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
     */
    public enum Counter {

        /** 单 IP 的登录请求总数。在<b>规则里</b> +1（每次登录请求都算一次） */
        IP_ATTEMPT("IP_ATTEMPT_LIMIT", "login:ip:attempt:", Duration.ofHours(1)),

        /** 单 IP 的登录失败数。只在<b>真的失败时</b> +1 */
        IP_FAIL("IP_FAIL_LIMIT", "login:ip:fail:", Duration.ofHours(1)),

        /** 单 IP 尝试过的不存在账号数。查无此人时 +1，同时也算一次 {@link #IP_FAIL} */
        IP_UNKNOWN("IP_UNKNOWN_LIMIT", "login:ip:unknown:", Duration.ofHours(1)),

        /** 单 IP 尝试过的<b>不同身份</b>（集合，存身份摘要）。不论账号存不存在都算 */
        IP_IDENTITY("IP_ACCOUNT_FANOUT", "login:ip:ids:", Duration.ofHours(1)),

        /** 单设备的登录次数（含失败）。在规则里 +1 */
        DEVICE_LOGIN("DEVICE_LOGIN_LIMIT", "dev:login:", Duration.ofDays(1)),

        /** 单设备的登录失败数。只在真的失败时 +1 */
        DEVICE_FAIL("DEVICE_FAIL_LIMIT", "dev:fail:", Duration.ofHours(1)),

        /**
         * 单设备碰过的<b>不同会员</b>（集合）。
         *
         * <p>换滑动窗口之后它不再需要特例：成员各自到点掉出去，
         * 既不会「整个集合在首次 add 的 24 小时后消失」（那正是养号要的效果），
         * 也不会「只要设备还活着，半年前碰过的号还算在内」。
         */
        DEVICE_MEMBER("DEVICE_FANOUT", "dev:member:", Duration.ofDays(1)),

        /** 单设备注册的账号数。注册还没进引擎，窗口由 DeviceGuardProperties 说 */
        DEVICE_REGISTER(null, "dev:reg:member:", Duration.ofDays(1)),
        ;

        private final String ruleCode;

        private final String prefix;

        private final Duration defaultWindow;

        Counter(String ruleCode, String prefix, Duration defaultWindow) {
            this.ruleCode = ruleCode;
            this.prefix = prefix;
            this.defaultWindow = defaultWindow;
        }
    }

    /**
     * 一个窗口切几个桶。
     *
     * <p>60：一小时窗口的精度是 ±1 分钟，而这几条规则的阈值是「几十次」量级 ——
     * 一分钟的误差无关紧要。桶数就是一个键的字段上限，所以内存与攻击量无关。
     */
    private static final int BUCKETS = 60;

    /**
     * 去重计数最多留几个成员。
     *
     * <p>内存的硬上限：被撞库时一个 IP 碰过的账号数等于<b>攻击量</b>，而阈值只有几十 ——
     * 丢掉最旧的那些不会改变「超没超」的结论。同 {@code AccountFailTracker} 的做法。
     */
    private static final int MAX_SET_ENTRIES = 500;

    private final RedisService redisService;

    /**
     * 🔴 用 {@link ObjectProvider} 延迟取，不在构造器里注入。
     *
     * <p>{@code RiskRuleRegistry} 的构造器会把所有 {@code RiskRule} bean 实例化出来做双向校验，
     * 而规则都依赖本类 —— 构造器直接注入 registry 就是一个循环依赖，Spring 会在启动时报错。
     */
    private final ObjectProvider<RiskRuleRegistry> registry;

    public LoginCounters(RedisService redisService, ObjectProvider<RiskRuleRegistry> registry) {
        this.redisService = redisService;
        this.registry = registry;
    }

    /** 「现在」只取一次，记一次与读一次必须落在同一个「现在」上（见 {@code slidingWindowCount}） */
    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * 一个计数器此刻的样子：窗口内累计值 + 最旧那个桶的序号。
     *
     * <p>把两者一起返回，是因为「超没超」和「还要多久降下来」要落在<b>同一次</b>读上 ——
     * 分两次读的话，两个答案来自两个时刻，而这个窗口每秒都在往前滑。
     */
    private record Window(long count, long oldestBucket) {

        static final Window EMPTY = new Window(0L, -1L);
    }

    /** 计数 +1，返回<b>含这一次</b>的累计值。维度值为空时返回 0（当作没数到） */
    public long bump(Counter counter, String value) {
        return count(counter, value, 1L).count();
    }

    /**
     * 只读，不加。
     *
     * <p>🔴 失败类计数器<b>必须只读不加</b>：在判断那一步 +1 的话，每一次正常登录也被算成一次失败，
     * 阈值当场失去意义。
     */
    public long read(Counter counter, String value) {
        return count(counter, value, 0L).count();
    }

    /**
     * 滑动窗口的那一次往返。
     *
     * <p>读不出来时当作 0：一个算不出来的计数器不该让整个网段登不进来 ——
     * 与改造前「值不是数字就当 0」同一个取向。
     */
    private Window count(Counter counter, String value, long delta) {
        if (SolvelaStringUtil.isBlank(value)) {
            return Window.EMPTY;
        }
        try {
            java.util.List<Long> result = redisService.slidingWindowCount(
                    key(counter, value), windowSeconds(counter), BUCKETS, nowSeconds(), delta);
            return new Window(result.get(0) == null ? 0L : result.get(0),
                    result.size() < 2 || result.get(1) == null ? -1L : result.get(1));
        } catch (RuntimeException e) {
            log.warn("【风控】读写 {} 失败，当作没数到", counter, e);
            return Window.EMPTY;
        }
    }

    /** 往集合里加一个成员，返回窗口内去重后的数量 */
    public long addToSet(Counter counter, String value, String member) {
        if (SolvelaStringUtil.isBlank(value) || SolvelaStringUtil.isBlank(member)) {
            return 0L;
        }
        try {
            return redisService.slidingSetCount(key(counter, value), member,
                    windowSeconds(counter) * 1000L, System.currentTimeMillis(), MAX_SET_ENTRIES);
        } catch (RuntimeException e) {
            log.warn("【风控】写 {} 失败，当作没数到", counter, e);
            return 0L;
        }
    }

    /**
     * 这个计数器还要多久降下来；至少 1 秒 —— 回「请 0 秒后重试」等于没说。
     *
     * <p>🔴 滑动窗口里<b>不能用键的 TTL</b> 回答这件事：TTL 是从最后一次写入算的，
     * 而先掉出去的是最早那一批。用 TTL 的话，一个还在被打的 IP 会被告知「等一小时」，
     * 而其实它再过一分钟就降到阈值以下了。
     *
     * <p>⚠️ 集合类计数器（{@link Counter#IP_IDENTITY} / {@link Counter#DEVICE_MEMBER}）
     * 落到按窗口长度兜底：那两个的「最旧成员」要再查一次 ZSET，而它们的处置档
     * 本来就是滑块（给的是「去拖图」而不是「等多久」），这个数只进文案。
     */
    public long retryAfterSeconds(Counter counter, String value) {
        if (SolvelaStringUtil.isBlank(value)) {
            return 1L;
        }
        long window = windowSeconds(counter);
        Window current = count(counter, value, 0L);
        if (current.oldestBucket() < 0) {
            return Math.max(1L, window);
        }
        return solvela.base.module.redis.RedisService.slidingWindowRetryAfterSeconds(
                window, BUCKETS, nowSeconds(), current.oldestBucket());
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
