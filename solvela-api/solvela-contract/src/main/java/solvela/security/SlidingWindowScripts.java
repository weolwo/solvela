package solvela.security;

/**
 * 滑动窗口计数的 <b>Lua 源码</b>与结果换算 —— 网关与 app-biz 共用的唯一一份。
 *
 * <h3>为什么放在 contract，而且只放字符串</h3>
 * 风控（app-biz，经 {@code RedisService}）与网关限流（solvela-app）数的是同一种东西。
 * 各写一份脚本的话，两边的窗口语义迟早不一样 —— 比如一边修了「只读也续期」的 bug，另一边没修，
 * 表现是「同一个 IP 在风控里早该解封了，在网关里还被挡着」，没有任何报错。
 *
 * <p>网关刻意不依赖 {@code solvela-base-redis}（Redisson + 一串 netty，见 {@code solvela-app/pom.xml} 的注释），
 * 而本模块不依赖 Redis。所以这里只放<b>脚本源码</b>与纯计算，两边各自用自己的
 * {@code StringRedisTemplate} 把它包成 {@code RedisScript} —— 与 {@link solvela.trace.TraceContract}
 * 「约定共用、实现各写」是同一个做法。
 *
 * <p>⚠️ 改这里等于改两个进程的计数语义：已经在 Redis 里的键会按新脚本解释。
 * 改了值的结构（哈希 ↔ ZSET 之类），键名必须跟着换（见 {@code LoginCounters} 类注释里 {@code w2:} 的教训）。
 *
 * @Date 2026-10-17
 */
public final class SlidingWindowScripts {

    private SlidingWindowScripts() {
    }

    /**
     * 分桶滑动窗口计数，一次往返、原子完成。
     *
     * <p>KEYS[1] = 计数键；ARGV = 窗口秒数、桶数、现在（秒）、增量（0 = 只读）。
     * 返回 {@code {窗口内累计值, 窗口内最旧那个桶的序号（没有数据为 -1）}}。
     *
     * <p>桶号 = {@code floor(now / bucketSeconds)}，窗口内的桶是 {@code [cur - buckets + 1, cur]}。
     * 字段就是桶号，值是那个桶里的次数。
     *
     * <h3>为什么是分桶，不是逐条记时间戳</h3>
     * 逐条记（ZSET，一次请求一个成员）能做到精确，但<b>内存随攻击量线性增长</b> ——
     * 而「被攻击」正是最不希望某个 IP 能让我们吃掉几百兆内存的时刻。分桶把一个键的字段数钉死在桶数上。
     * 实际覆盖的时间在 {@code [window, window + window/buckets)} 之间，偏<b>严</b>的一侧。
     *
     * <p>🔴 {@code EXPIRE} 只在写入时设：只读也续期的话，一个一直被查询（而没人再写入）的键永远不过期。
     * 读写都顺手删掉窗口外的桶：不删的话，一个持续活跃的键字段只增不减。
     */
    public static final String COUNT = """
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
            """;

    /**
     * 滑动窗口<b>去重</b>计数（ZSET），一次往返、原子完成。
     *
     * <p>KEYS[1] = 集合键；ARGV = 成员（空串 = 只读）、窗口毫秒数、现在（毫秒）、成员上限（≤ 0 = 不限）。
     * 返回窗口内的去重数量。
     *
     * <p>同一个成员再出现一次要刷新分数，不是忽略：分数不刷新的话，一个持续被碰的账号会在窗口末尾掉出去。
     * 成员上限是内存的硬上限 —— 被撞库时一个 IP 碰过的账号数等于攻击量，而阈值只有几十。
     *
     * <p>🔴 修剪（按分数、按数量）在 ZCARD <b>之前</b>做：反过来的话返回的数量里含着已经过期的成员。
     */
    public static final String DISTINCT = """
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
            """;

    /**
     * 「还要多久这个滑动计数才会降下来」= 最旧那个桶滑出窗口还要多久。
     *
     * <p>🔴 不能用键的 TTL 回答这件事：TTL 是从<b>最后一次写入</b>算的，而滑动窗口里先掉出去的是<b>最早</b>那一批。
     * 用 TTL 的话，一个还在被攻击的 IP 会被告知「等一小时」，而其实它再过一分钟就降到阈值以下了。
     *
     * @param oldestBucket {@link #COUNT} 返回的第二个值
     * @return 至少 1 秒 —— 回「请 0 秒后重试」等于没说
     */
    public static long retryAfterSeconds(long windowSeconds, int buckets, long nowSeconds, long oldestBucket) {
        if (oldestBucket < 0) {
            return 1L;
        }
        long bucketSeconds = Math.max(1L, windowSeconds / Math.max(1, buckets));
        // 最旧那个桶的起点 + 窗口 = 它滑出窗口的时刻
        long expiresAt = (oldestBucket + buckets) * bucketSeconds;
        return Math.max(1L, expiresAt - nowSeconds);
    }
}
