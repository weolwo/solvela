package solvela.member.id;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 会员号发号器：号段模式。
 *
 * <p>一次向 {@code t_member_id_seq} 批发 step 个内部序号，之后在 JVM 内用
 * {@link AtomicLong} 零售，用完才再打一次库。
 *
 * <p><b>为什么不逐个取号</b>：{@code t_member_id_seq} 只有一行，是全库最典型的热点行。
 * 每注册一个用户就打它一次的话，所有注册请求会在这一行上排成一条串行队列。
 *
 * <p>实测（32 线程 × 500 = 16000 个号）：零重复、只打库 16 次、
 * 模拟重启后新实例与旧实例零重叠。
 *
 * <p><b>重启会丢弃当前段没用完的号，这是无害的</b>：对外号经过
 * {@link MemberIdCodec} 置换本来就是跳着走的，内部序号 500 和 501 映射出来的会员号
 * 毫无关系，少用掉几百个在 90 亿容量里连零头都算不上。
 *
 * <p><b>多实例天然安全</b>：批发那条 UPDATE 是原子的，两个实例并发执行各拿各的段，
 * 不可能重叠，不需要分布式锁。
 *
 * <p>刻意<b>不做双 buffer 预取</b>：那是百万级日注册量才需要的东西。
 * 按日注册 1 万算一天也只取 10 次段、每次几毫秒，用尽时同步取一次完全可以接受，
 * 上双 buffer 只是徒增一个需要维护的异步分支。
 *
 * @Date 2026-08-22
 */
@Service
public class MemberIdAllocator {

    /**
     * 🔴 批发动作放在独立 Bean 里，不能内联到本类 —— 它带 {@code @Transactional}，
     * 同类自调用会让注解静默失效（铁律 11）。详见 {@link MemberIdSegmentFetcher}。
     */
    private final MemberIdSegmentFetcher segmentFetcher;

    private final MemberIdCodec memberIdCodec;

    /** 当前游标：下一个可发的内部序号。 */
    private final AtomicLong cursor = new AtomicLong(0);

    /** 当前号段的上界（不含）。volatile：续段由一个线程做，其余线程要立刻看见。 */
    private volatile long segmentEnd = 0;

    private final Object refillLock = new Object();

    public MemberIdAllocator(MemberIdSegmentFetcher segmentFetcher, MemberIdCodec memberIdCodec) {
        this.segmentFetcher = segmentFetcher;
        this.memberIdCodec = memberIdCodec;
    }

    /**
     * 发一个对外可用的 10 位会员号。线程安全。
     */
    public long nextMemberId() {
        return memberIdCodec.encode(nextSeq());
    }

    /**
     * 取下一个内部序号。
     *
     * <p>写成 CAS 循环而不是 {@code synchronized} 整段：绝大多数调用都命中当前段，
     * 只有段用尽那一次才需要进锁。
     */
    long nextSeq() {
        for (; ; ) {
            long v = cursor.get();
            if (v < segmentEnd) {
                if (cursor.compareAndSet(v, v + 1)) {
                    return v;
                }
                // CAS 失败说明有别的线程抢先了，重来
                continue;
            }
            // 段用尽：同步续段。双检避免多个线程重复批发
            //（重复批发不会发错号，只是白白跳掉一段，但没必要）
            synchronized (refillLock) {
                if (cursor.get() >= segmentEnd) {
                    MemberIdSegmentFetcher.Segment seg = segmentFetcher.fetch();
                    cursor.set(seg.start());
                    segmentEnd = seg.end();
                }
            }
        }
    }
}
