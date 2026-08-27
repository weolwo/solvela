package solvela.admin.redis;

import solvela.base.module.redis.RedisService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 钉住固定窗口计数的两条性质：计数正确，以及<b>键一定带 TTL</b>。
 *
 * <p>第二条才是重点。原来的实现是 INCR 一条、EXPIRE 一条，两条命令之间只要中断
 * （进程被 kill、连接断开、主从切换），就会留下一个永不过期的计数键 ——
 * 之后这个会员的登录限制、这个活动的频次限制<b>再也不会自己解除</b>，
 * 而代码和数据库里都看不出任何异常。这类 bug 不会在开发环境出现，只会在生产上偶发。
 *
 * <p>这里没法真的模拟"两条命令之间崩溃"，所以退一步验证等价的事实：
 * 脚本执行后键必须有 TTL；以及一个已经存在、故意抹掉 TTL 的坏键，下一次计数会把它治好。
 */
@SpringBootTest
class RedisIncrementTest {

    private static final String KEY = "test:increment:窗口";

    @Autowired
    private RedisService redisService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void clean() {
        stringRedisTemplate.delete(KEY);
    }

    @Test
    @DisplayName("计数递增，且第一次就带上 TTL")
    void 计数与过期一次完成() {
        assertEquals(1L, redisService.increment(KEY, 60));
        Long ttl = stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60, "第一次计数就必须带 TTL，实际 = " + ttl);

        assertEquals(2L, redisService.increment(KEY, 60));
        assertEquals(3L, redisService.increment(KEY, 60));
    }

    @Test
    @DisplayName("TTL 不会被后续计数续期 —— 固定窗口，不是滑动窗口")
    void 后续计数不续期() throws Exception {
        redisService.increment(KEY, 100);
        Long first = stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS);

        Thread.sleep(1100);
        redisService.increment(KEY, 100);
        Long second = stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS);

        assertTrue(second < first,
                "TTL 被续期了：窗口永远不会结束，一个慢速脚本能把计数一直累到锁定。"
                        + " first=" + first + " second=" + second);
    }

    @Test
    @DisplayName("历史遗留的无 TTL 坏键会被下一次计数治好")
    void 修复没有过期时间的坏键() {
        // 手工造一个「INCR 成功、EXPIRE 没跑」留下的键
        stringRedisTemplate.opsForValue().set(KEY, "7");
        assertEquals(-1L, stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS),
                "-1 表示键存在但没有过期时间，这正是要修的状态");

        assertEquals(8L, redisService.increment(KEY, 60));
        Long ttl = stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS);
        assertTrue(ttl > 0, "坏键没被补上 TTL，它会永远卡住这个维度的限流");
    }
}
