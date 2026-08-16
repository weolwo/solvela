package sa.lottery.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FPE 发号器 单元测试（纯内存，无 Spring 上下文）
 *
 * 最核心的一条是「全域遍历零重复零遗漏」：它不是抽样验证，而是<b>双射的完整证明</b>——
 * 域内每个游标都算一遍，得到的号码集合恰好等于整个域，
 * 这直接推出「游标不重复 ⇒ 号码不重复」，也就是整套发号方案的立身之本。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
class FpeCipherTest {

    private static final String SECRET = "unit-test-master-secret-do-not-use-in-prod";

    private static final String LOTTERY = "H88JHKJFNE";

    private static final String ISSUE = "2026_MID_01";

    private FpeCipher cipher(int numberLength) {
        return FpeCipher.of(SECRET, LOTTERY, ISSUE, numberLength);
    }

    // ==================== 双射（最重要） ====================

    @Test
    @DisplayName("4位全域遍历：1 万个游标产出 1 万个互不相同的号码，且恰好铺满整个域")
    void bijectionOnMinimumDomain() {
        assertBijection(4, 10_000);
    }

    @Test
    @DisplayName("5位全域遍历：10 万个游标零重复零遗漏（奇数长度，会真实触发 cycle-walking）")
    void bijectionOnOddDomain() {
        assertBijection(5, 100_000);
    }

    /**
     * 遍历 [0, domain) 的每一个游标，断言产出的号码集合恰好是 [0, domain) 本身。
     * 用 BitSet 而不是 HashSet：10 万个 Long 装箱的开销没必要，且 BitSet 天然能查"有没有空洞"。
     */
    private void assertBijection(int numberLength, int domain) {
        FpeCipher cipher = cipher(numberLength);
        assertEquals(domain, cipher.domain(), "号码空间应为 10^" + numberLength);

        BitSet seen = new BitSet(domain);
        for (int sequenceNo = 0; sequenceNo < domain; sequenceNo++) {
            String ticket = cipher.encrypt(sequenceNo);

            assertEquals(numberLength, ticket.length(), "号码必须补齐到固定长度：" + ticket);
            int value = Integer.parseInt(ticket);
            assertTrue(value >= 0 && value < domain, "号码越界：" + ticket);
            // 重复即意味着两个用户会拿到同一个号码，是本方案最不能出的错
            assertTrue(!seen.get(value), "号码重复：sequenceNo=" + sequenceNo + " 又一次产出了 " + ticket);
            seen.set(value);
        }
        assertEquals(domain, seen.cardinality(), "号码集合有空洞，说明不是满射");
    }

    // ==================== 可逆性 ====================

    @Test
    @DisplayName("可逆：decrypt(encrypt(x)) == x，全域成立（客服验真、对账自证清白依赖它）")
    void reversible() {
        FpeCipher cipher = cipher(4);
        for (int sequenceNo = 0; sequenceNo < 10_000; sequenceNo++) {
            String ticket = cipher.encrypt(sequenceNo);
            assertEquals(sequenceNo, cipher.decrypt(ticket), "反解不回去：" + ticket);
        }
    }

    // ==================== 密钥派生 ====================

    @Test
    @DisplayName("同 key 同输入结果稳定：重建实例也必须产出一样的号码，否则重启后号码全变")
    void deterministic() {
        assertEquals(cipher(5).encrypt(12_304), cipher(5).encrypt(12_304));
    }

    @Test
    @DisplayName("换期号则输出完全不同：issueNo 是 tweak，这是表里不需要盐值列的前提")
    void issueNoActsAsTweak() {
        FpeCipher issue01 = FpeCipher.of(SECRET, LOTTERY, "2026_MID_01", 5);
        FpeCipher issue02 = FpeCipher.of(SECRET, LOTTERY, "2026_MID_02", 5);

        int differ = 0;
        for (int i = 0; i < 1_000; i++) {
            if (!issue01.encrypt(i).equals(issue02.encrypt(i))) {
                differ++;
            }
        }
        // 两个独立置换在同一点重合的概率约 1/100000，1000 次里几乎不该有重合
        assertTrue(differ >= 995, "换期号后号码几乎应当全变，实际只变了 " + differ + "/1000");
    }

    @Test
    @DisplayName("换彩票编码同样改变输出：lotteryCode 参与密钥派生，推演台缺它就会算错")
    void lotteryCodeAffectsKey() {
        assertNotEquals(FpeCipher.of(SECRET, "H88JHKJFNE", ISSUE, 5).encrypt(1),
                FpeCipher.of(SECRET, "ZZ99ABCDEF", ISSUE, 5).encrypt(1));
    }

    // ==================== 边界与非法入参 ====================

    @Test
    @DisplayName("号码长度下限 4：3 位被拒（小域下分布质量差、且 1000 个号可被穷举）")
    void rejectTooShort() {
        assertThrows(IllegalArgumentException.class, () -> cipher(3));
        assertThrows(IllegalArgumentException.class, () -> cipher(10));
    }

    @Test
    @DisplayName("密钥派生的三个入参缺一不可")
    void rejectBlankKeyMaterial() {
        assertThrows(IllegalArgumentException.class, () -> FpeCipher.of("  ", LOTTERY, ISSUE, 5));
        assertThrows(IllegalArgumentException.class, () -> FpeCipher.of(SECRET, null, ISSUE, 5));
        assertThrows(IllegalArgumentException.class, () -> FpeCipher.of(SECRET, LOTTERY, "", 5));
    }

    @Test
    @DisplayName("游标越界立即抛错：卖到最后一张时的 off-by-one 只在这里暴露")
    void rejectOutOfRangeSequence() {
        FpeCipher cipher = cipher(4);
        assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(-1));
        // Redis 的 INCR 是 1-indexed，若调用方忘了减 1，卖满时就会拿 domain 本身来算号
        assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(10_000));
        // 边界内的最后一个游标必须正常工作
        assertEquals(4, cipher.encrypt(9_999).length());
    }

    @Test
    @DisplayName("非法号码解密被拒：长度不符、含非数字")
    void rejectMalformedTicket() {
        FpeCipher cipher = cipher(4);
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("123"));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("12345"));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("12A4"));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(null));
    }

    // ==================== 分布质量 ====================

    @Test
    @DisplayName("最小域(4位)首位数字分布做卡方检验：这一档过了，更大的域只会更好")
    void chiSquareOnSmallestDomain() {
        FpeCipher cipher = cipher(4);
        long[] buckets = new long[10];
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            buckets[cipher.encrypt(i).charAt(0) - '0']++;
        }

        double expected = total / 10.0;
        double chiSquare = 0;
        for (long observed : buckets) {
            chiSquare += Math.pow(observed - expected, 2) / expected;
        }
        // 自由度 9、显著性 0.001 的临界值是 27.877；超过它才认为分布显著不均
        assertTrue(chiSquare < 27.877,
                "首位数字分布显著偏斜，卡方值=" + chiSquare + "，各桶=" + java.util.Arrays.toString(buckets));
    }

    @Test
    @DisplayName("相邻游标产出的号码不相邻：否则用户能顺推下一个号提前占号")
    void adjacentSequencesAreNotAdjacent() {
        FpeCipher cipher = cipher(5);
        Set<Integer> gaps = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            gaps.add(Math.abs(Integer.parseInt(cipher.encrypt(i + 1)) - Integer.parseInt(cipher.encrypt(i))));
        }
        // 连续发号时差值恒为 1；这里应当散得到处都是
        assertTrue(gaps.size() > 150, "相邻游标的号码差值过于集中，可预测性偏高：" + gaps.size() + "/200");
    }
}
