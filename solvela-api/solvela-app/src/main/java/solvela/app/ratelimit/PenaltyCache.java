package solvela.app.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内的「在处罚中」短缓存（网关方案 §5.5）。
 *
 * <p>只缓存<b>肯定</b>的结论：被封的 IP 每秒打几千次，也只在每个缓存周期里去一次 Redis。
 * 不缓存「没被罚」—— 那样的话处罚刚写下去，要等缓存过期才生效，而蜜罐、关键字要的恰恰是「下一次就挡」。
 *
 * <p>代价：处罚被提前解除（后台撤销）时，最多晚 {@link #TTL_MILLIS} 放行，可以接受。
 *
 * <h3>容量</h3>
 * 满了就整个清掉，不做 LRU：被打满意味着正在被大量不同的 IP 打，这时缓存命中率本来就低，
 * 清掉的代价只是下一轮各去一次 Redis。比引一个缓存库、或者让这张表无限长大都划算。
 *
 * @Date 2026-10-17
 */
final class PenaltyCache {

    static final long TTL_MILLIS = 5_000;

    static final int MAX_ENTRIES = 10_000;

    /** @param untilEpochSeconds 处罚到期（epoch 秒）；0 = 没有到期时间（设备封禁、长期黑名单） */
    record Blocked(long untilEpochSeconds) {
    }

    private record Cached(Blocked blocked, long cachedUntilMillis) {
    }

    private final ConcurrentHashMap<String, Cached> entries = new ConcurrentHashMap<>();

    /** 缓存里还有效的处罚；没有为 null */
    Blocked get(String key, long nowMillis) {
        Cached c = entries.get(key);
        if (c == null) {
            return null;
        }
        if (c.cachedUntilMillis() <= nowMillis) {
            entries.remove(key, c);
            return null;
        }
        return c.blocked();
    }

    void put(String key, Blocked blocked, long nowMillis) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        long until = nowMillis + TTL_MILLIS;
        if (blocked.untilEpochSeconds() > 0) {
            // 处罚比缓存先到期时，缓存不能比处罚活得久
            until = Math.min(until, blocked.untilEpochSeconds() * 1000);
        }
        entries.put(key, new Cached(blocked, until));
    }

    int size() {
        return entries.size();
    }
}
