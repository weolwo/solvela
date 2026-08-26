package solvela.lottery.runtime;

import solvela.base.exception.BusinessException;
import solvela.lottery.engine.FpeCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发号游标换算的边界测试（纯内存，不需要 Redis）。
 *
 * <p>为什么值得单独一个测试类：{@code cursor -> sequenceNo} 这个 ±1 是全链路唯一的换算点，
 * 而它出错的表现<b>只在「把一期完整卖光」的那一刻才暴露</b> ——
 * 平时抽样领几个号，怎么算都不会越界，常规测试永远覆盖不到。
 *
 * <p>这里把 {@link LotterySequenceService#toSequenceNo} 的纯计算部分拿出来单测：
 * Redis 交互（INCR / 冷启动回源）需要真实 Redis，放到集成验证里跑。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
class SequenceCursorTest {

    private static final String SECRET = "unit-test-master-secret";

    /**
     * 复刻 LotterySequenceService.toSequenceNo 的判定。
     * 之所以复刻而不是注入真实 Bean：该方法不依赖任何字段，纯计算；
     * 为它拉起 Spring 上下文只会让这组边界测试变慢且更脆。
     */
    private long toSequenceNo(long cursor, int totalCount, long domain) {
        long sequenceNo = cursor - 1;
        if (sequenceNo < 0 || sequenceNo >= totalCount || totalCount > domain) {
            throw new BusinessException("发号游标越界：cursor=" + cursor + ", totalCount=" + totalCount
                    + ", domain=" + domain + "（期望 0 <= cursor-1 < totalCount <= domain）");
        }
        return sequenceNo;
    }

    @Test
    @DisplayName("Redis 是 1-indexed，sequenceNo 是 0-indexed：首个游标 1 对应 sequenceNo 0")
    void firstCursorMapsToZero() {
        assertEquals(0, toSequenceNo(1, 10_000, 10_000));
    }

    @Test
    @DisplayName("★ 卖光最后一张：cursor == totalCount 必须成功，得到 totalCount-1")
    void lastTicketIsValid() {
        // 发行量拉满到号码空间上限，这是最容易越界的配置
        assertEquals(9_999, toSequenceNo(10_000, 10_000, 10_000));
    }

    @Test
    @DisplayName("★ 卖光之后：cursor == totalCount+1 必须被拒，而不是算出一个越界的号")
    void oneOverIsRejected() {
        // 若这里放行，FPE 会拿到 domain 本身作为输入 —— 定义域是 [0, domain)，直接越界
        assertThrows(BusinessException.class, () -> toSequenceNo(10_001, 10_000, 10_000));
    }

    @Test
    @DisplayName("游标不可能为 0 或负数：INCR 从 1 起，出现 0 说明有人手工改过 key")
    void nonPositiveCursorRejected() {
        assertThrows(BusinessException.class, () -> toSequenceNo(0, 10_000, 10_000));
        assertThrows(BusinessException.class, () -> toSequenceNo(-1, 10_000, 10_000));
    }

    @Test
    @DisplayName("发行量超过号码空间直接拒绝：配置校验若被绕过，这里是最后一道")
    void totalCountBeyondDomainRejected() {
        assertThrows(BusinessException.class, () -> toSequenceNo(1, 20_000, 10_000));
    }

    @Test
    @DisplayName("★ 端到端卖光模拟：4位号发行量拉满，1 万个游标产出 1 万个互不相同的号码，无空洞无越界")
    void sellOutEntireIssue() {
        int totalCount = 10_000;
        FpeCipher cipher = FpeCipher.of(SECRET, "H88JHKJFNE", "2026_SELLOUT", 4);
        assertEquals(totalCount, cipher.domain());

        BitSet seenNumbers = new BitSet(totalCount);
        BitSet seenSequences = new BitSet(totalCount);

        for (long cursor = 1; cursor <= totalCount; cursor++) {
            long sequenceNo = toSequenceNo(cursor, totalCount, cipher.domain());
            assertTrue(!seenSequences.get((int) sequenceNo), "游标重复：" + sequenceNo);
            seenSequences.set((int) sequenceNo);

            String ticket = cipher.encrypt(sequenceNo);
            int value = Integer.parseInt(ticket);
            assertTrue(!seenNumbers.get(value), "号码重复：cursor=" + cursor + " 又产出了 " + ticket);
            seenNumbers.set(value);
        }

        assertEquals(totalCount, seenSequences.cardinality(), "游标集合有空洞");
        assertEquals(totalCount, seenNumbers.cardinality(), "号码集合有空洞");
        // 卖光之后再来一个必须被挡住
        assertThrows(BusinessException.class, () -> toSequenceNo(totalCount + 1, totalCount, cipher.domain()));
    }

    @Test
    @DisplayName("发行量小于号码空间时，号码在空间里稀疏分布（空间大发行少是好事，更难被猜中）")
    void sparseIssuance() {
        int totalCount = 500;
        FpeCipher cipher = FpeCipher.of(SECRET, "H88JHKJFNE", "2026_SPARSE", 5);
        BitSet seen = new BitSet(100_000);
        for (long cursor = 1; cursor <= totalCount; cursor++) {
            seen.set(Integer.parseInt(cipher.encrypt(toSequenceNo(cursor, totalCount, cipher.domain()))));
        }
        assertEquals(totalCount, seen.cardinality(), "稀疏发行也不能重号");
        assertThrows(BusinessException.class, () -> toSequenceNo(totalCount + 1, totalCount, cipher.domain()));
    }
}
