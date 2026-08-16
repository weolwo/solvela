package sa.lottery.engine;

import sa.lottery.constant.LotteryConst;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

/**
 * 格式保留加密（FPE）发号器：把连续游标变成不可预测但保证不重复的号码。
 *
 * <pre>
 * sequenceNo (0,1,2,3...)  --encrypt-->  ticketNumber ("85210","03471",...)
 *                          &lt;--decrypt--
 * </pre>
 *
 * <h3>为什么发号要用它</h3>
 * 号码必须同时满足两件相互矛盾的事：<b>绝不重复</b>（否则同一号码两个人中奖）
 * 与<b>不可预测</b>（否则用户能算出下一个号提前占号）。
 * 直接发连续号满足前者不满足后者；随机发号满足后者却要靠查重兜底，高并发下是灾难。
 * FPE 是一个定义域上的<b>双射</b>，于是「游标不重复」直接推出「号码不重复」，
 * 不需要号池、不需要查重，{@code uk_issue_ticket} 从主要防线降级成兜底断言。
 *
 * <p>可逆性不是附赠品而是刚需：客服拿到一个号码能反解出游标，
 * 即可判定它是否为本期真实发出的号，对账时能自证清白。
 *
 * <h3>算法：带 cycle-walking 的 6 轮平衡 Feistel</h3>
 * 定义域 {@code N = 10^numberLength}，取 {@code a = ceil(sqrt(N))}，
 * 在 {@code Z_a × Z_a} 上跑平衡 Feistel，落在 {@code [N, a²)} 之外的结果就再加密一次（cycle-walking）。
 *
 * <p><b>为什么不用不平衡 Feistel</b>：平衡 Feistel 在 {@code Z_a × Z_a} 上
 * <b>无论轮函数 F 是什么都恒为置换</b>——这是 Feistel 结构的代数性质，不依赖 F 的密码学强度。
 * 不平衡版本要在两个不同模数间交替，边界条件容易写错，而写错的后果是号码重复。
 * 最不能出错的地方，选最难写错的写法。
 *
 * <p>cycle-walking 的代价可精确计算，全长度区间都是白菜价：
 * <pre>
 * 长度  N        a=⌈√N⌉  a²          期望加密次数 a²/N
 *  4    10^4       100   10,000      1.000  （完全平方，零 walking）
 *  5    10^5       317   100,489     1.005
 *  6    10^6     1,000   1,000,000   1.000
 *  7    10^7     3,163   10,004,569  1.0005
 *  9    10^9    31,623   ~1.00001e9  1.00001
 * </pre>
 * ⚠️ 不要图省事把 a 取成 {@code 10^⌈L/2⌉}（如 L=5 取 1000）：那样域是 10^6，期望迭代次数会变成 10 倍。
 *
 * <h3>密钥不落库</h3>
 * {@code key = HMAC-SHA256(masterSecret, lotteryCode + "|" + issueNo)}，
 * masterSecret 放配置文件，issueNo 天然充当 tweak——这就是表里不需要盐值列的原因。
 *
 * <p><b>两条不可变约束（改了会让历史号码无法验证）</b>：
 * masterSecret 一旦有期号发过号就不可轮换；issueNo 创建后不可修改。
 *
 * <p>本类不可变、线程安全。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
public final class FpeCipher {

    /**
     * Feistel 轮数。Luby-Rackoff 定理下 3 轮即 PRP、4 轮即强 PRP；
     * 小域下攻击面更大，取 6 留余量。纯内存 HMAC，单次耗时微秒级。
     */
    private static final int ROUNDS = 6;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * 10 的幂查表：避免用 Math.pow 得到 999999.9999999999 这类浮点结果
     */
    private static final long[] POW10 = {
            1L, 10L, 100L, 1_000L, 10_000L, 100_000L,
            1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L
    };

    /**
     * Mac.getInstance 相对昂贵而 init 很廉价，故按线程复用实例、每次使用前重新 init。
     * Mac 本身不是线程安全的，ThreadLocal 是这里最省心的隔离方式。
     */
    private static final ThreadLocal<Mac> MAC_HOLDER = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance(HMAC_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 " + HMAC_ALGORITHM, e);
        }
    });

    private final SecretKeySpec keySpec;

    private final int numberLength;

    /**
     * 号码空间 10^numberLength
     */
    private final long domain;

    /**
     * Feistel 的半边模数，a² >= domain
     */
    private final long half;

    private final String numberFormat;

    private FpeCipher(SecretKeySpec keySpec, int numberLength, long domain, long half) {
        this.keySpec = keySpec;
        this.numberLength = numberLength;
        this.domain = domain;
        this.half = half;
        this.numberFormat = "%0" + numberLength + "d";
    }

    /**
     * 按「彩票 + 期号」派生一个发号器
     *
     * @param masterSecret 系统级主密钥（配置文件下发，不落库、不可轮换）
     * @param lotteryCode  彩票编码
     * @param issueNo      期号，充当 tweak：同一游标在不同期号下得到完全不同的号码
     * @param numberLength 号码长度，取值 [4, 9]
     */
    public static FpeCipher of(String masterSecret, String lotteryCode, String issueNo, int numberLength) {
        if (masterSecret == null || masterSecret.isBlank()) {
            throw new IllegalArgumentException("FPE 主密钥未配置");
        }
        if (lotteryCode == null || lotteryCode.isBlank()) {
            throw new IllegalArgumentException("彩票编码不能为空：它参与密钥派生，缺了它算出的号码与线上对不上");
        }
        if (issueNo == null || issueNo.isBlank()) {
            throw new IllegalArgumentException("期号不能为空：它是 FPE 的 tweak");
        }
        if (numberLength < LotteryConst.MIN_NUMBER_LENGTH || numberLength > LotteryConst.MAX_NUMBER_LENGTH) {
            throw new IllegalArgumentException("号码长度必须在 " + LotteryConst.MIN_NUMBER_LENGTH
                    + "~" + LotteryConst.MAX_NUMBER_LENGTH + " 之间，当前 " + numberLength);
        }

        byte[] key = hmac(new SecretKeySpec(masterSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM),
                (lotteryCode + "|" + issueNo).getBytes(StandardCharsets.UTF_8));
        long domain = POW10[numberLength];
        return new FpeCipher(new SecretKeySpec(key, HMAC_ALGORITHM), numberLength, domain, ceilSqrt(domain));
    }

    /**
     * 号码空间大小 10^numberLength。调用方用它校验 totalCount 是否越界
     */
    public long domain() {
        return domain;
    }

    /**
     * 游标 -> 号码
     *
     * @param sequenceNo 0-indexed 游标，取值 [0, domain)。
     *                   ⚠️ Redis 的 INCR 是 1-indexed，调用方必须先减 1 再传进来
     */
    public String encrypt(long sequenceNo) {
        if (sequenceNo < 0 || sequenceNo >= domain) {
            throw new IllegalArgumentException("游标越界：sequenceNo=" + sequenceNo + " 不在 [0, " + domain + ") 内");
        }
        long value = sequenceNo;
        // cycle-walking：落在 [domain, half²) 的结果再加密一次，直到回到有效域
        do {
            value = feistel(value);
        } while (value >= domain);
        return String.format(numberFormat, value);
    }

    /**
     * 号码 -> 游标。号码非法或不属于本期时抛 IllegalArgumentException
     */
    public long decrypt(String ticketNumber) {
        if (ticketNumber == null || ticketNumber.length() != numberLength) {
            throw new IllegalArgumentException("号码长度必须是 " + numberLength + " 位：" + ticketNumber);
        }
        long value = 0;
        for (int i = 0; i < ticketNumber.length(); i++) {
            char c = ticketNumber.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("号码只能是数字：" + ticketNumber);
            }
            value = value * 10 + (c - '0');
        }
        // cycle-walking 的逆：正向怎么走的，反向就怎么退回来
        do {
            value = feistelInverse(value);
        } while (value >= domain);
        return value;
    }

    /**
     * 平衡 Feistel 正向：无论轮函数 F 是什么，本变换在 [0, half²) 上恒为置换
     */
    private long feistel(long x) {
        long left = x / half;
        long right = x % half;
        for (int round = 0; round < ROUNDS; round++) {
            long next = (left + roundFunction(round, right)) % half;
            left = right;
            right = next;
        }
        return left * half + right;
    }

    /**
     * 平衡 Feistel 逆向：倒着跑每一轮，把加上去的 F 再减回去
     */
    private long feistelInverse(long y) {
        long left = y / half;
        long right = y % half;
        for (int round = ROUNDS - 1; round >= 0; round--) {
            long previousRight = left;
            // 正向是 right = (left + F(round, right)) % half，故反向要减掉同一个 F
            long previousLeft = Math.floorMod(right - roundFunction(round, previousRight), half);
            left = previousLeft;
            right = previousRight;
        }
        return left * half + right;
    }

    /**
     * 轮函数：取 HMAC 的前 8 字节对 half 取模。
     * 这里的取模偏差不影响正确性——Feistel 的置换性质对任意 F 都成立，F 只影响「看起来有多随机」
     */
    private long roundFunction(int round, long right) {
        byte[] input = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).putInt(round).putLong(right).array();
        byte[] digest = hmac(keySpec, input);
        long raw = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        return Math.floorMod(raw, half);
    }

    private static byte[] hmac(SecretKeySpec keySpec, byte[] data) {
        try {
            Mac mac = MAC_HOLDER.get();
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /**
     * 精确的 ceil(sqrt(n))：先用浮点估一个再上下校正，避免 Math.sqrt 的精度误差
     * 让 half² &lt; domain（那会直接导致号码重复）
     */
    private static long ceilSqrt(long n) {
        long root = (long) Math.ceil(Math.sqrt((double) n));
        while (root * root < n) {
            root++;
        }
        while (root > 1 && (root - 1) * (root - 1) >= n) {
            root--;
        }
        return root;
    }
}
