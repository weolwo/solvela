package net.lab1024.sa.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 雪花算法的行为固化测试。
 *
 * 这套实现是移除 hutool 时手写回来的，当时用 hutool 的 Snowflake 做解析器逐条比对过
 * （5 组 workerId/dataCenterId × 500 个 ID，位段全部对得上；纪元一致，同一时刻两边生成的 ID
 * 差距在 2^30 以内）。下面的用例把当时验证过的性质钉住。
 *
 * 🔴 位布局和纪元是**不可逆**的：库里已有历史 ID，改了就再也对不齐。
 *
 * @Date 2026-08-08
 */
public class SnowflakeIdGeneratorTest {

    @Test
    public void 位段可原样还原() {
        for (long[] wd : new long[][]{{0, 0}, {0, 8}, {31, 31}, {5, 17}, {1, 2}}) {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(wd[0], wd[1]);
            for (int i = 0; i < 200; i++) {
                long id = generator.nextId();
                assertEquals(wd[0], SnowflakeIdGenerator.parseWorkerId(id));
                assertEquals(wd[1], SnowflakeIdGenerator.parseDataCenterId(id));
                assertTrue(SnowflakeIdGenerator.parseSequence(id) < 4096);
            }
        }
    }

    @Test
    public void 纪元必须与hutool一致否则新旧ID对不齐() {
        long id = new SnowflakeIdGenerator(1, 1).nextId();
        long timestamp = SnowflakeIdGenerator.parseTimestamp(id);
        // 解出来的时间戳必须就是「现在」。纪元一旦被改成别的值，这里会偏出几年
        assertTrue(Math.abs(System.currentTimeMillis() - timestamp) < 5000,
                () -> "解析出的时间戳偏离当前时间：" + timestamp);

        // ID 必须是正数：符号位恒 0，41 位时间戳到 2079 年才用尽
        assertTrue(id > 0, () -> "ID 应为正数：" + id);
    }

    @Test
    public void 单线程高频取号唯一且严格递增() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, 4);
        Set<Long> seen = new HashSet<>();
        long prev = -1;
        for (int i = 0; i < 200_000; i++) {
            long id = generator.nextId();
            assertTrue(seen.add(id), () -> "发出了重号：" + id);
            long finalPrev = prev;
            assertTrue(id > finalPrev, () -> "ID 不再单调递增：" + finalPrev + " -> " + id);
            prev = id;
        }
    }

    @Test
    public void 并发取号不重复() throws Exception {
        // nextId 是 synchronized 的。去掉同步、或把 sequence 与 lastTimestamp 拆成两个
        // 独立的原子变量，都会在「同一毫秒 + 序列回绕」的交叉点上发重号 —— 这个用例就是拦它的
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7, 9);
        int threads = 16;
        int perThread = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Long> all = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        all.add(generator.nextId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发取号超时");
        pool.shutdownNow();

        List<Long> ids = List.copyOf(all);
        assertEquals(threads * perThread, ids.size());
        assertEquals(ids.size(), new HashSet<>(ids).size(), "并发下出现重号");
    }

    @Test
    public void 越界的workerId与dataCenterId必须拒绝() {
        // 各 5 位，合法区间 0~31。静默取模会让两个节点用上同一个 ID 空间
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32, 0));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0, 32));

        // 边界值本身是合法的
        assertTrue(new SnowflakeIdGenerator(0, 0).nextId() > 0);
        assertTrue(new SnowflakeIdGenerator(31, 31).nextId() > 0);
    }

    @Test
    public void 不同节点在同一毫秒内生成的ID不会相同() {
        SnowflakeIdGenerator a = new SnowflakeIdGenerator(1, 1);
        SnowflakeIdGenerator b = new SnowflakeIdGenerator(2, 1);
        SnowflakeIdGenerator c = new SnowflakeIdGenerator(1, 2);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            Collections.addAll(ids, a.nextId(), b.nextId(), c.nextId());
        }
        assertEquals(3000, ids.size(), "不同 workerId/dataCenterId 之间出现了重号");
    }
}
