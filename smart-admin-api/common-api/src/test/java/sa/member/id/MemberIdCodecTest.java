package sa.member.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会员号置换的行为固化测试。
 *
 * <p>钉住的是四条<b>算法性质</b>，不是实现细节：双射、可逆、位数、不泄露顺序。
 * 只要这四条还成立，内部怎么改都行；有一条不成立，线上就会出「重复会员号」
 * 或者「注册量被外部推算出来」。
 *
 * <p>🔴 密钥与位布局是<b>不可逆</b>的：库里已有历史会员号，改了就再也对不齐。
 *
 * @Date 2026-08-22
 */
public class MemberIdCodecTest {

    /** 测试专用密钥。与生产配置无关，改它不影响线上。 */
    private static final String TEST_KEY = "member-id-codec-unit-test-key";

    private static MemberIdCodec codec(String secret) {
        MemberIdProperties p = new MemberIdProperties();
        p.setSecretKey(secret);
        return new MemberIdCodec(p);
    }

    @Test
    public void 是双射所以绝不重复() {
        MemberIdCodec codec = codec(TEST_KEY);
        int n = 200_000;
        Set<Long> seen = new HashSet<>(n * 2);
        for (long seq = 0; seq < n; seq++) {
            assertTrue(seen.add(codec.encode(seq)), "第 " + seq + " 个序号产生了重复会员号");
        }
        assertEquals(n, seen.size());
    }

    @Test
    public void 可逆_能从会员号反推内部序号() {
        MemberIdCodec codec = codec(TEST_KEY);
        for (long seq = 0; seq < 50_000; seq++) {
            assertEquals(seq, codec.decode(codec.encode(seq)));
        }
        // 边界：首、尾
        for (long seq : new long[]{0, 1, MemberIdCodec.CAPACITY - 1}) {
            assertEquals(seq, codec.decode(codec.encode(seq)));
        }
    }

    @Test
    public void 恒为十位且落在约定区间内() {
        MemberIdCodec codec = codec(TEST_KEY);
        long max = MemberIdCodec.MIN + MemberIdCodec.CAPACITY;
        for (long seq = 0; seq < 100_000; seq++) {
            long id = codec.encode(seq);
            assertTrue(id >= MemberIdCodec.MIN && id < max, "越界: " + id);
            assertEquals(10, String.valueOf(id).length(), "不是 10 位: " + id);
        }
    }

    /**
     * 不泄露注册顺序 —— 这是「不用自增值当会员号」的全部理由。
     *
     * <p>判据用「连续同向最长游程」：顺序自增会得到 n-1（一路递增），
     * 而真随机源约为 5。这里同时跑一个 {@link Random} 作为基准，
     * 避免把「我以为随机应该是多少」当成判据 ——
     * 第一次写这个用例时就凭直觉估成了 14，实测基准才是 5。
     */
    @Test
    public void 相邻序号的会员号看不出顺序() {
        MemberIdCodec codec = codec(TEST_KEY);
        int n = 20_000;

        long[] permuted = new long[n];
        for (int i = 0; i < n; i++) {
            permuted[i] = codec.encode(1000 + i);
        }
        long[] random = new long[n];
        Random rnd = new Random(42);
        for (int i = 0; i < n; i++) {
            random[i] = MemberIdCodec.MIN + (long) (rnd.nextDouble() * MemberIdCodec.CAPACITY);
        }
        long[] sequential = new long[n];
        for (int i = 0; i < n; i++) {
            sequential[i] = MemberIdCodec.MIN + i;
        }

        long runPermuted = maxSameDirectionRun(permuted);
        long runRandom = maxSameDirectionRun(random);
        long runSequential = maxSameDirectionRun(sequential);

        // 顺序自增必然是一路同向；置换必须远离这个形态
        assertEquals(n - 2, runSequential);
        assertTrue(runPermuted < 20,
                "置换后仍有长游程 " + runPermuted + "，说明顺序泄露了");
        // 与真随机源同量级即可，不要求精确相等
        assertTrue(Math.abs(runPermuted - runRandom) <= 5,
                "置换游程 " + runPermuted + " 与随机基准 " + runRandom + " 差距过大");
    }

    @Test
    public void 换密钥会得到完全不同的映射() {
        MemberIdCodec a = codec(TEST_KEY);
        MemberIdCodec b = codec(TEST_KEY + "-different");
        int same = 0;
        for (long seq = 0; seq < 1000; seq++) {
            if (a.encode(seq) == b.encode(seq)) {
                same++;
            }
        }
        // 这条用例的意义不是「验证功能」，而是<b>把「密钥不可改」这件事钉在测试里</b>：
        // 谁要是改了生产密钥，历史会员号就再也对不上了。
        assertTrue(same <= 1, "换密钥后映射几乎没变，置换没有真正依赖密钥");
    }

    @Test
    public void 缺密钥直接启动失败而不是用默认值() {
        MemberIdProperties p = new MemberIdProperties();
        assertThrows(IllegalStateException.class, () -> new MemberIdCodec(p));
        p.setSecretKey("   ");
        assertThrows(IllegalStateException.class, () -> new MemberIdCodec(p));
    }

    @Test
    public void 越界输入被拒绝() {
        MemberIdCodec codec = codec(TEST_KEY);
        assertThrows(RuntimeException.class, () -> codec.encode(-1));
        assertThrows(RuntimeException.class, () -> codec.encode(MemberIdCodec.CAPACITY));
        assertThrows(RuntimeException.class, () -> codec.decode(MemberIdCodec.MIN - 1));
        assertThrows(RuntimeException.class, () -> codec.decode(MemberIdCodec.MIN + MemberIdCodec.CAPACITY));
    }

    @Test
    public void 同一密钥跨实例结果一致() {
        // 发号器是有状态的，但编解码必须是纯函数 —— 否则重启后同一个序号会映射到别的号
        MemberIdCodec a = codec(TEST_KEY);
        MemberIdCodec b = codec(TEST_KEY);
        for (long seq = 0; seq < 5000; seq++) {
            assertEquals(a.encode(seq), b.encode(seq));
        }
        assertFalse(a == b);
    }

    /** 相邻元素「同为上升或同为下降」的最长连续段。 */
    private static long maxSameDirectionRun(long[] v) {
        long run = 0;
        long max = 0;
        boolean last = true;
        for (int i = 1; i < v.length; i++) {
            boolean up = v[i] > v[i - 1];
            run = (i > 1 && up == last) ? run + 1 : 0;
            max = Math.max(max, run);
            last = up;
        }
        return max;
    }
}
