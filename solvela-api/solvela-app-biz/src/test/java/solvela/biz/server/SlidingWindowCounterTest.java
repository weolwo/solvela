package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.module.redis.RedisService;
import solvela.security.SlidingWindowScripts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滑动窗口计数（{@code RedisService.slidingWindowCount} / {@code slidingSetCount}）。连真 Redis。
 *
 * <h3>为什么必须连真 Redis</h3>
 * 两段逻辑都在 <b>Lua 里</b>。mock 掉 {@code StringRedisTemplate} 就等于把被测对象整个换掉 ——
 * 剩下能验的只有「参数传对了没有」，而写错的那几种（修剪顺序、桶号算法、只读也续期）
 * 一条都测不到。
 *
 * <h3>钉住的核心那一条：边界不再加倍</h3>
 * 固定窗口的毛病是窗口快结束时打满一次、跨过边界再打满一次 ——
 * 两分钟内拿到两倍额度，而每个窗口的计数都「没超」。这正是攻击者有动机去卡的地方（方案 §4.3）。
 *
 * <p>⚠️ 这里用<b>调用方传进去的「现在」</b>来模拟时间推进，不 sleep：
 * 窗口是小时级的，等真实时间过去不是办法。而「现在」由调用方传本身也是刻意的设计
 * （同一次请求里的记一次与读一次必须落在同一个「现在」上）。
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class SlidingWindowCounterTest {

    /** 一小时窗口、60 个桶 —— 与 {@code LoginCounters} 用的一样 */
    private static final long WINDOW = 3600L;

    private static final int BUCKETS = 60;

    private static final long BUCKET_SECONDS = WINDOW / BUCKETS;

    @Autowired
    private RedisService redisService;

    private final List<String> keys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        if (!keys.isEmpty()) {
            redisService.delete(keys.toArray(new String[0]));
            keys.clear();
        }
    }

    // ============================== 计数 ==============================

    @Test
    @DisplayName("记一次就加一次，累计值含这一次")
    void 累计() {
        String key = freshKey();
        long now = 1_700_000_000L;

        assertEquals(1L, bump(key, now));
        assertEquals(2L, bump(key, now));
        assertEquals(3L, bump(key, now));
        assertEquals(3L, read(key, now), "只读不该把自己也数进去");
    }

    @Test
    @DisplayName("🔴 边界不再加倍：窗口末尾打满 + 刚过边界再打，第二批仍然看得见第一批")
    void 边界不加倍() {
        String key = freshKey();
        long now = 1_700_000_000L;

        // 第 1 分钟打 10 次
        for (int i = 0; i < 10; i++) {
            bump(key, now);
        }
        // 59 分钟后（还在窗口内）再打 10 次
        long later = now + 59 * BUCKET_SECONDS;
        for (int i = 0; i < 10; i++) {
            bump(key, later);
        }

        assertEquals(20L, read(key, later),
                "固定窗口在这里会因为「跨过了边界」只看到 10 —— 于是两分钟内拿到两倍额度");
    }

    @Test
    @DisplayName("窗口滑过去之后，老的那一批自己掉出去")
    void 老数据掉出窗口() {
        String key = freshKey();
        long now = 1_700_000_000L;
        for (int i = 0; i < 10; i++) {
            bump(key, now);
        }

        // 整整一个窗口之后
        long later = now + WINDOW + BUCKET_SECONDS;

        assertEquals(0L, read(key, later));
        assertEquals(1L, bump(key, later), "新的一批从 1 开始，而不是接着 10 往上加");
    }

    @Test
    @DisplayName("逐桶滑动：每过一个桶，最早那个桶的计数就掉出去一份")
    void 逐桶滑动() {
        String key = freshKey();
        long now = 1_700_000_000L;
        // 连续 60 个桶，每个桶打一次 → 窗口内正好 60
        for (int i = 0; i < BUCKETS; i++) {
            bump(key, now + i * BUCKET_SECONDS);
        }
        long last = now + (BUCKETS - 1) * BUCKET_SECONDS;
        assertEquals(BUCKETS, read(key, last));

        // 再往后走一个桶：最早那个掉出去
        assertEquals(BUCKETS - 1, read(key, last + BUCKET_SECONDS));
        assertEquals(BUCKETS - 2, read(key, last + 2 * BUCKET_SECONDS));
    }

    @Test
    @DisplayName("🔴 只读不续期 —— 否则一个一直被查询、没人再写入的键永远不过期")
    void 只读不续期() {
        String key = freshKey();
        long now = 1_700_000_000L;
        bump(key, now);
        long afterWrite = redisService.getExpire(key);

        read(key, now);

        long afterRead = redisService.getExpire(key);
        assertTrue(afterRead <= afterWrite,
                "只读把 TTL 往后推了：afterWrite=" + afterWrite + ", afterRead=" + afterRead);
    }

    @Test
    @DisplayName("「还要多久降下来」按最旧那个桶算，不是按键的 TTL")
    void 还要多久降下来() {
        String key = freshKey();
        long now = 1_700_000_000L;
        bump(key, now);
        // 半个窗口之后又打一次：最旧的那一批还有半个窗口才掉出去
        long later = now + WINDOW / 2;
        bump(key, later);

        long oldest = redisService.slidingWindowCount(key, WINDOW, BUCKETS, later, 0L).get(1);
        long retryAfter = SlidingWindowScripts.retryAfterSeconds(WINDOW, BUCKETS, later, oldest);

        assertTrue(retryAfter > 0 && retryAfter <= WINDOW / 2 + BUCKET_SECONDS,
                "最旧那一批应当在约半个窗口后掉出去，实际 " + retryAfter);
    }

    @Test
    @DisplayName("窗口外的桶会被顺手删掉 —— 不删的话活跃的键字段只增不减")
    void 顺手清理老桶() {
        String key = freshKey();
        long now = 1_700_000_000L;
        for (int i = 0; i < 5; i++) {
            bump(key, now + i * BUCKET_SECONDS);
        }
        // 跨过一整个窗口再写一次：之前那 5 个桶都该被清掉，只剩新的那一个
        bump(key, now + 10 * WINDOW);

        assertEquals(1L, read(key, now + 10 * WINDOW));
    }

    // ============================== 去重计数 ==============================

    @Test
    @DisplayName("去重：同一个成员记多少次都只算一个")
    void 去重() {
        String key = freshKey();
        long now = System.currentTimeMillis();

        assertEquals(1L, addToSet(key, "a", now));
        assertEquals(1L, addToSet(key, "a", now));
        assertEquals(2L, addToSet(key, "b", now));
    }

    @Test
    @DisplayName("🔴 重复出现要刷新「最近一次」，否则一直在场的成员会在窗口末尾掉出去")
    void 重复出现刷新() {
        String key = freshKey();
        long now = System.currentTimeMillis();
        addToSet(key, "a", now);

        // 大半个窗口之后 a 又出现一次 → 它的「最近一次」变成现在
        long later = now + WINDOW * 1000L * 3 / 4;
        addToSet(key, "a", later);

        // 从第一次算已经超过一个窗口，但从「最近一次」算还在窗口内
        long muchLater = now + WINDOW * 1000L + 1000L;
        assertEquals(1L, addToSet(key, null, muchLater), "刷新没生效的话这里是 0");
    }

    @Test
    @DisplayName("窗口外的成员掉出去（修剪在 ZCARD 之前做）")
    void 成员掉出窗口() {
        String key = freshKey();
        long now = System.currentTimeMillis();
        addToSet(key, "a", now);
        addToSet(key, "b", now);

        assertEquals(0L, addToSet(key, null, now + WINDOW * 1000L + 1000L),
                "修剪放在 ZCARD 之后的话，返回的数量里会含着已经过期的成员");
    }

    @Test
    @DisplayName("成员数有硬上限 —— 被撞库时一个 IP 碰过的账号数等于攻击量")
    void 成员上限() {
        String key = freshKey();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            addToSet(key, "m" + i, now + i, 10);
        }

        assertEquals(10L, addToSet(key, null, now + 100, 10),
                "丢掉最旧的那些不会改变「超没超」的结论，但内存必须有界");
    }

    // ============================== 工具 ==============================

    private long bump(String key, long nowSeconds) {
        return redisService.slidingWindowCount(key, WINDOW, BUCKETS, nowSeconds, 1L).get(0);
    }

    private long read(String key, long nowSeconds) {
        return redisService.slidingWindowCount(key, WINDOW, BUCKETS, nowSeconds, 0L).get(0);
    }

    private long addToSet(String key, String member, long nowMillis) {
        return addToSet(key, member, nowMillis, 0);
    }

    private long addToSet(String key, String member, long nowMillis, int maxEntries) {
        return redisService.slidingSetCount(key, member, WINDOW * 1000L, nowMillis, maxEntries);
    }

    private String freshKey() {
        String key = redisService.generateRedisKey("test:sliding:",
                String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L)));
        keys.add(key);
        return key;
    }
}
