package solvela.lottery.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.common.exception.BusinessException;
import solvela.lottery.record.dao.LotteryRecordDao;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 发号游标服务：一期一个 Redis 计数器，{@code INCR} 拿槽位。
 *
 * <h3>核心不变量：游标只增不减</h3>
 * 对比抽奖的库存预扣（失败要 rollback 回滚 Redis），这里方向<b>正好相反</b>：
 * <pre>
 * 回滚游标 = 两个用户拿到同一个 sequenceNo = 同一个号码 = uk_issue_ticket 冲突
 * </pre>
 * 浪费一个号（留个空洞）是可接受的，发重号不是。
 * <b>任何在发号路径上写 DECR 的改动都要按资损处理。</b>
 *
 * <h3>基准：Redis 1-indexed，sequenceNo 0-indexed，换算只在一处</h3>
 * {@code INCR} 对空 key 返回 1，顺着它来不跟它拧着写；而 FPE 的定义域是 {@code [0, N)}，
 * 于是 {@code sequenceNo = cursor - 1}，且<b>这是全链路唯一的 ±1 换算点</b>。
 *
 * <p>为什么把换算放在 Redis 这一侧而不是加密那一侧：两个边界的容错完全不对等。
 * Redis 边界算错 ⇒ 跳一个号，可观测、可接受；
 * 加密边界算错 ⇒ 越界或号码错位，动摇的是「号码不重复」这个立身之本。
 * 所以换算固定在容错高的那一侧，并用断言兜住。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LotterySequenceService {

    private final RedissonClient redissonClient;
    private final LotteryRecordDao lotteryRecordDao;

    /**
     * 冷启动回源锁的等待/持有时长。回源只是一次 MAX 查询，很快
     */
    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 10;

    /**
     * 取下一个游标（1-indexed）。返回值 > totalCount 即表示售罄。
     *
     * <p>不在这里判售罄而是把原始游标交给调用方，是为了让「售罄」这个业务判定
     * 留在编排层，本服务只负责「发一个不重复的槽位」这一件事。
     */
    public long nextCursor(String lotteryCode, String issueNo) {
        ensureWarmed(lotteryCode, issueNo);
        return redissonClient.getAtomicLong(LotteryCacheKey.sequence(lotteryCode, issueNo)).incrementAndGet();
    }

    /**
     * 游标 -> FPE 输入。<b>全链路唯一的 ±1 换算点。</b>
     *
     * @param cursor      Redis INCR 的返回值，1-indexed
     * @param totalCount  本期发行上限
     * @param domain      号码空间 10^numberLength
     * @return 0-indexed 的 sequenceNo，保证落在 [0, totalCount) 内
     */
    public long toSequenceNo(long cursor, int totalCount, long domain) {
        long sequenceNo = cursor - 1;
        // 断言而不是静默修正：这三个边界任何一条不成立，都说明上游算错了，
        // 继续算号只会产出一个「看起来正常、实际错位」的号码，那比直接失败危险得多
        if (sequenceNo < 0 || sequenceNo >= totalCount || totalCount > domain) {
            throw new BusinessException("发号游标越界：cursor=" + cursor + ", totalCount=" + totalCount
                    + ", domain=" + domain + "（期望 0 <= cursor-1 < totalCount <= domain）");
        }
        return sequenceNo;
    }

    /**
     * 冷启动回源：Redis key 丢失（重启/驱逐/被误清）时，按 DB 现状补齐游标。
     *
     * <p>交接文档第 3 轮压测的教训是「只重置 DB 等于没重置」；在彩票场景里失效模式更危险 ——
     * <b>Redis 游标偏低会直接导致重号</b>。所以这里有三重讲究：
     * <ol>
     *   <li><b>分布式锁</b>：避免多个线程同时回源，各自读到不同的 MAX 又各自写入；</li>
     *   <li><b>SET NX</b>：即使抢锁失败也绝不覆盖别人已经写好的值；</li>
     *   <li><b>取 MAX 而不是 COUNT</b>：有空洞时 COUNT 会偏低，回填后必然撞号。</li>
     * </ol>
     *
     * <p>写入值 = {@code MAX(sequence_no) + 1} = 已消耗的槽位数，
     * 这样下一次 {@code INCR} 恰好接上；无记录时写 0，首次 INCR 得 1 → sequenceNo 0。
     */
    private void ensureWarmed(String lotteryCode, String issueNo) {
        String key = LotteryCacheKey.sequence(lotteryCode, issueNo);
        if (redissonClient.getBucket(key, StringCodec.INSTANCE).isExists()) {
            return;
        }
        RLock lock = redissonClient.getLock(LotteryCacheKey.sequenceLock(lotteryCode, issueNo));
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            // 双检：等锁期间别的线程可能已经回源完了
            if (redissonClient.getBucket(key, StringCodec.INSTANCE).isExists()) {
                return;
            }
            Long maxSequenceNo = lotteryRecordDao.selectMaxSequenceNo(lotteryCode, issueNo);
            long consumed = maxSequenceNo == null ? 0L : maxSequenceNo + 1;
            // 即便没抢到锁也走 SET NX：补空洞可以，覆盖绝对不行
            boolean written = redissonClient.getBucket(key, StringCodec.INSTANCE)
                    .setIfAbsent(String.valueOf(consumed));
            log.warn("[彩票发号] 游标冷启动回源 lotteryCode={}, issueNo={}, DB内MAX={}, 写入={}, 实际生效={}",
                    lotteryCode, issueNo, maxSequenceNo, consumed, written);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("发号游标初始化被中断，请重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 当前游标快照，仅供运营查看/对账，不参与发号
     */
    public long currentCursor(String lotteryCode, String issueNo) {
        Object value = redissonClient.getBucket(LotteryCacheKey.sequence(lotteryCode, issueNo), StringCodec.INSTANCE).get();
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
