package solvela.base.module.redis;

import jakarta.annotation.Resource;
import solvela.base.domain.SystemEnvironment;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.base.constant.StringConst;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 上<b>做了决策</b>的那几件事：环境隔离的 key 前缀，以及原子的计数窗口（固定窗口与滑动窗口）。
 *
 * <h3>🔴 这不是 RedisUtil，请不要往这里加转发方法</h3>
 * 判据只有一条：<b>一个方法值不值得放进来，看它替调用方做掉了多少决策</b>。
 * {@code set(k, v, ttl)} 什么都没决定，它只是把 {@code opsForValue().set} 抄了一遍，
 * 还顺手丢掉了 {@link StringRedisTemplate} 的泛型、pipeline 与批量能力 ——
 * 加这种方法不是封装，是多一层需要维护的转述。
 *
 * <p>要封装就封装<b>业务能力</b>：令牌存取去看 {@code MemberTokenStore}，
 * 那一层的调用方看到的是 {@code issue / resolve / revoke}，根本不知道底下是 Redis；
 * key 结构去看 {@code DrawCacheKey} / {@code LotteryCacheKey}，
 * 把「运维要按什么模式扫」这件事定在一处。这两种都比 RedisUtil 有价值得多。
 *
 * <p>这条规矩是被实证过的：本类原有 19 个 public 方法，其中 13 个（{@code hasKey}、
 * {@code expire}、{@code mset}、{@code mget}、{@code getObject}、若干 {@code set} 重载……）
 * 从落地起<b>一个调用方都没有</b>，写了两年没人用；而唯一被三处业务同时依赖的
 * {@link #increment}，恰好是唯一一个有决策的方法。2026-09-05 已清理，
 * 同批删掉的还有 {@code getLock / unLock} —— 那是一对没有 owner 校验的 SETNX 土锁
 * （任何人都能 unlock 别人持有的锁），分布式锁统一走 Redisson 的 {@code @RedisLock}。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2020/8/25 21:57
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class RedisService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SystemEnvironment systemEnvironment;

    /**
     * 拼出带环境隔离的 key：{@code 项目名:环境:前缀+key}。
     *
     * <p>前两段是隔离位。开发、测试、预发经常共用一个 Redis 实例，少了它，
     * 测试环境的一次「清验证码」会把预发用户的验证码一起清掉，而两边日志都正常。
     */
    public String generateRedisKey(String prefix, String key) {
        SystemEnvironmentEnum currentEnvironment = systemEnvironment.getCurrentEnvironment();
        return systemEnvironment.getProjectName() + StringConst.COLON + currentEnvironment.getValue()
                + StringConst.COLON + prefix + key;
    }

    /**
     * 计数 +1，并<b>只在第一次</b>计数时打上过期时间，返回累计值。
     *
     * <p>「只在第一次」是关键：每次都 expire 会让 TTL 被不断续期，窗口永远不结束 ——
     * 一个每 29 分钟试一次密码的脚本能把计数一直累加到锁定，而它其实从没在任一 30 分钟窗口里
     * 超过阈值。这里的语义是<b>固定窗口</b>：第一次失败起算，TTL 到点整个计数清零。
     *
     * <p>🔴 <b>INCR 与 EXPIRE 必须在同一个脚本里</b>，不能写成两条命令。
     * INCR 自己是原子的，但「INCR 完再 EXPIRE」这一对不是：两条命令之间应用被 kill、
     * 连接断开、或 Redis 主从切换，都会留下一个<b>没有 TTL 的计数键</b>。
     * 那个键此后永不过期，计数只增不减 —— 表现是这个会员/这个活动的限流<b>再也不会解除</b>，
     * 而且查不出原因：代码看着没问题，数据库里也没有任何限制记录。
     *
     * <p>脚本里还多了一层保险：即使键已存在却没有 TTL（历史遗留的坏键），
     * 也会补上过期时间，让老数据自己愈合。
     *
     * @param expireSeconds 窗口长度（秒）
     */
    public long increment(String key, long expireSeconds) {
        Long count = stringRedisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(expireSeconds));
        return count == null ? 0L : count;
    }

    /**
     * <b>滑动窗口</b>计数：记一次（或只读），返回最近 {@code windowSeconds} 内的累计值。
     *
     * <h3>它解决固定窗口的什么问题</h3>
     * {@link #increment} 是固定窗口：第一次计数起算，TTL 到点整个计数清零。
     * 后果是<b>边界加倍</b> —— 窗口快结束时打满一次、跨过边界再打满一次，
     * 实际两分钟内拿到了两倍额度，而每个窗口的计数都「没超」。
     * 风控的那几条限流正好是攻击者有动机去卡这个边界的地方（方案 §4.3）。
     *
     * <h3>🔴 为什么是分桶，不是逐条记时间戳</h3>
     * 逐条记（ZSET，一次请求一个成员）能做到精确，但<b>内存随攻击量线性增长</b> ——
     * 而「被攻击」正是最不希望某个 IP 能让我们吃掉几百兆内存的时刻。
     * 分桶把一个键的字段数钉死在 {@code buckets} 个：一小时 60 个桶，精度 ±1 分钟，
     * 而这几条规则的阈值是「几十次」量级，一分钟的误差无关紧要。
     *
     * <h3>精度的准确说法</h3>
     * 实际覆盖的时间在 {@code [window, window + window/buckets)} 之间 ——
     * 当前这个桶只过了一部分，但它整个被算进来。这是分桶法固有的，偏<b>严</b>的一侧
     * （宁可多算一点，不可漏算）。
     *
     * <p>读写都顺手把窗口外的桶删掉：{@code HGETALL} 已经把字段都拿回来了，删它们不要钱。
     * 不删的话，一个持续活跃的键每过一个桶就多一个字段，而 TTL 又被写入不断续期 —— 字段只增不减。
     *
     * @param delta 加几；<b>传 0 表示只读</b>（只读时不续期，否则一次查询就能把窗口往后推）
     * @param nowSeconds 调用方传「现在」，不在脚本里取 —— 同一次请求里的记一次与读一次
     *                   必须落在同一个「现在」上，否则两者会算出不同的窗口
     * @return 下标 0 = 窗口内累计值；下标 1 = 窗口内<b>最旧那个桶</b>的序号（没有数据时为 -1），
     *         调用方用它算「还要多久这个计数才会降下来」（见 {@link #slidingWindowRetryAfterSeconds}）
     */
    public List<Long> slidingWindowCount(String key, long windowSeconds, int buckets,
                                         long nowSeconds, long delta) {
        List<Long> result = stringRedisTemplate.execute(SLIDING_WINDOW, List.of(key),
                String.valueOf(windowSeconds), String.valueOf(buckets),
                String.valueOf(nowSeconds), String.valueOf(delta));
        return result == null ? List.of(0L, -1L) : result;
    }

    /**
     * 「还要多久这个滑动计数才会降下来」= 最旧那个桶滑出窗口还要多久。
     *
     * <p>🔴 不能用键的 TTL 回答这件事：TTL 是从<b>最后一次写入</b>算的，
     * 而滑动窗口里先掉出去的是<b>最早</b>那一批。用 TTL 的话，一个还在被攻击的 IP
     * 会被告知「等一小时」，而其实它再过一分钟就降到阈值以下了。
     *
     * @param oldestBucket {@link #slidingWindowCount} 返回的下标 1
     * @return 至少 1 秒 —— 回「请 0 秒后重试」等于没说
     */
    public static long slidingWindowRetryAfterSeconds(long windowSeconds, int buckets,
                                                      long nowSeconds, long oldestBucket) {
        if (oldestBucket < 0) {
            return 1L;
        }
        long bucketSeconds = Math.max(1L, windowSeconds / Math.max(1, buckets));
        // 最旧那个桶的起点 + 窗口 = 它滑出窗口的时刻
        long expiresAt = (oldestBucket + buckets) * bucketSeconds;
        return Math.max(1L, expiresAt - nowSeconds);
    }

    /**
     * 滑动窗口计数，一次往返、原子完成。
     *
     * <p>桶号 = {@code floor(now / bucketSeconds)}，窗口内的桶是
     * {@code [cur - buckets + 1, cur]}。字段就是桶号，值是那个桶里的次数。
     *
     * <p>🔴 {@code EXPIRE} 只在写入时设：只读也续期的话，一个一直在被查询
     * （而没人再写入）的键永远不过期。
     */
    private static final RedisScript<List> SLIDING_WINDOW = RedisScript.of("""
            local window = tonumber(ARGV[1])
            local buckets = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            local bucketSeconds = math.floor(window / buckets)
            if bucketSeconds < 1 then bucketSeconds = 1 end
            local cur = math.floor(now / bucketSeconds)
            local cutoff = cur - buckets + 1
            if delta > 0 then
                redis.call('HINCRBY', KEYS[1], tostring(cur), delta)
                redis.call('EXPIRE', KEYS[1], window + bucketSeconds)
            end
            local total = 0
            local oldest = -1
            local stale = {}
            local flat = redis.call('HGETALL', KEYS[1])
            for i = 1, #flat, 2 do
                local bucket = tonumber(flat[i])
                if bucket == nil or bucket < cutoff then
                    table.insert(stale, flat[i])
                else
                    total = total + tonumber(flat[i + 1])
                    if oldest < 0 or bucket < oldest then oldest = bucket end
                end
            end
            if #stale > 0 then
                redis.call('HDEL', KEYS[1], unpack(stale))
            end
            return {total, oldest}
            """, List.class);

    /**
     * <b>滑动窗口去重计数</b>：把一个成员记进集合（或只读），返回最近 {@code windowMillis} 内的<b>去重数量</b>。
     *
     * <h3>为什么这个用 ZSET 而不是分桶</h3>
     * 它数的是「几个<b>不同</b>的东西」（一个 IP 碰过几个账号、一台设备碰过几个会员）。
     * 分桶只能存计数，去重必须留成员本身。而成员数天然就是我们要测的那个量，
     * 再加上下面那个上限，内存是有界的。
     *
     * <h3>🔴 重复出现要刷新分数，不是忽略</h3>
     * 同一个成员再出现一次，它的「最近一次」就是现在 —— 分数不刷新的话，
     * 一个持续被碰的账号会在窗口末尾掉出去，而它其实一直在场。
     *
     * @param member     成员；<b>传 null 或空表示只读</b>（只读时不续期）
     * @param maxEntries 最多留几个成员，超了从最旧的开始丢；{@code <= 0} 表示不限。
     *                   它是内存的硬上限：被撞库时一个 IP 碰过的账号数等于攻击量，
     *                   而阈值只有几十 —— 丢掉最旧的那些不会改变「超没超」的结论
     */
    public long slidingSetCount(String key, String member, long windowMillis,
                                long nowMillis, int maxEntries) {
        Long count = stringRedisTemplate.execute(SLIDING_SET, List.of(key),
                member == null ? "" : member, String.valueOf(windowMillis),
                String.valueOf(nowMillis), String.valueOf(maxEntries));
        return count == null ? 0L : count;
    }

    /**
     * 滑动窗口去重计数，一次往返、原子完成。
     *
     * <p>🔴 修剪（按分数、按数量）在 ZCARD <b>之前</b>做：顺序反过来的话，
     * 返回的数量里会含着已经过期的成员 —— 而那正是固定窗口的毛病。
     */
    private static final RedisScript<Long> SLIDING_SET = RedisScript.of("""
            local member = ARGV[1]
            local window = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local maxEntries = tonumber(ARGV[4])
            if member ~= '' then
                redis.call('ZADD', KEYS[1], now, member)
                redis.call('PEXPIRE', KEYS[1], window)
            end
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            if maxEntries > 0 then
                redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -(maxEntries + 1))
            end
            return redis.call('ZCARD', KEYS[1])
            """, Long.class);

    /**
     * INCR + 首次设置 TTL，一次往返、原子完成。
     *
     * <p>{@code PTTL} 返回 -1 表示键存在但没有过期时间，-2 表示键不存在。
     * 新建（返回值 1）或发现没有 TTL 时都补上 —— 后者是为了修复历史上由非原子写法留下的坏键。
     */
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 or redis.call('PTTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return n
            """, Long.class);

    /**
     * 剩余过期秒数。-1 = 键存在但没有 TTL，-2 = 键不存在。
     *
     * <p>限流场景靠它把「还要等多久」告诉用户，所以这两个负值要原样透出去，
     * 不能归一成 0 —— 调用方分不清「已经解封」和「永远不会解封」。
     */
    public long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public String get(String key) {
        return key == null ? null : stringRedisTemplate.opsForValue().get(key);
    }

    public void set(String key, String value, long second) {
        stringRedisTemplate.opsForValue().set(key, value, second, TimeUnit.SECONDS);
    }

    /** 删除一个或多个 key。多个时走一次 DEL，不是循环单删。 */
    public void delete(String... key) {
        if (key == null || key.length == 0) {
            return;
        }
        stringRedisTemplate.delete(Arrays.asList(key));
    }
}
