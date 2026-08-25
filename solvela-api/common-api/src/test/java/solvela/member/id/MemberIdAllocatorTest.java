package solvela.member.id;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发号器的<b>并发正确性</b>测试。
 *
 * <p>用假的 {@link MemberIdSegmentFetcher} 替掉真库：这里要验的是
 * 「CAS 游标 + 双检续段」这段并发逻辑，不是 SQL。SQL 那部分靠
 * {@code MemberIdSeqDao} 的两条注解语句 + 真库联调覆盖。
 *
 * <p>🔴 一个反直觉的点：<b>重复批发号段是可以接受的</b>（只是跳号），
 * 但<b>同一个序号发两次是致命的</b>。所以用例盯的是后者。
 *
 * @Date 2026-08-22
 */
public class MemberIdAllocatorTest {

    private static final String TEST_KEY = "member-id-allocator-unit-test-key";

    /** 假批发器：内存里模拟 t_member_id_seq 的水位推进，并记录打库次数。 */
    static class FakeFetcher extends MemberIdSegmentFetcher {
        private final int step;
        private final AtomicLong water = new AtomicLong(0);
        final AtomicLong fetchCount = new AtomicLong(0);

        FakeFetcher(int step) {
            super(null, null);
            this.step = step;
        }

        @Override
        public Segment fetch() {
            fetchCount.incrementAndGet();
            long end = water.addAndGet(step);
            return new Segment(end - step, end);
        }
    }

    private static MemberIdCodec codec() {
        MemberIdProperties p = new MemberIdProperties();
        p.setSecretKey(TEST_KEY);
        return new MemberIdCodec(p);
    }

    @Test
    public void 并发发号不重复() throws Exception {
        int threads = 32;
        int perThread = 500;
        int total = threads * perThread;

        FakeFetcher fetcher = new FakeFetcher(1000);
        MemberIdAllocator allocator = new MemberIdAllocator(fetcher, codec());

        // 每个线程只走 nextMemberId 这一条路（生产上就是它），
        // 这样「发了多少次」和「拿到多少个号」是一一对应的，断言才干净
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        AtomicLong dupId = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        if (!ids.add(allocator.nextMemberId())) {
                            dupId.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发用例超时");
        pool.shutdown();

        assertEquals(0, dupId.get(), "会员号出现重复");
        assertEquals(total, ids.size(), "发出的号数量对不上：期望 " + total + " 个互不相同的会员号");
    }

    @Test
    public void 号段用尽才打库_不是每次都打() {
        FakeFetcher fetcher = new FakeFetcher(1000);
        MemberIdAllocator allocator = new MemberIdAllocator(fetcher, codec());
        for (int i = 0; i < 5000; i++) {
            allocator.nextSeq();
        }
        // 5000 个号 / 段长 1000 = 5 次；绝不该是 5000 次
        assertEquals(5, fetcher.fetchCount.get());
    }

    @Test
    public void 新实例接着水位发_不与旧实例重叠() {
        FakeFetcher shared = new FakeFetcher(100);
        MemberIdAllocator a = new MemberIdAllocator(shared, codec());
        MemberIdAllocator b = new MemberIdAllocator(shared, codec());

        Set<Long> fromA = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 250; i++) {
            fromA.add(a.nextSeq());
        }
        // b 相当于「重启后的新实例」：共享同一张 seq 表，各批各的段
        int overlap = 0;
        for (int i = 0; i < 250; i++) {
            if (fromA.contains(b.nextSeq())) {
                overlap++;
            }
        }
        assertEquals(0, overlap, "新旧实例发出了重叠的序号");
    }
}
