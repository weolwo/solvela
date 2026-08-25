package solvela.lottery.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 奖级规则的中奖概率模型（纯函数，无状态）。
 *
 * <h3>为什么概率是可以精确算出来的</h3>
 * 号码字符集固定十进制（{@code LotteryConst.NUMBER_CHARSET = "0-9"}），且 FPE 是双射 ——
 * 一期内发出的号码在号码空间里均匀不重复。于是一条规则的命中率只取决于它「锁死了几位」：
 * <pre>
 *   HEAD n  锁死前 n 位          → 命中率 10^-n
 *   TAIL n  锁死后 n 位          → 命中率 10^-n
 *   EXACT   锁死全部 L 位        → 命中率 10^-L
 * </pre>
 *
 * <h3>净中奖率：为什么不能直接用上面那个数</h3>
 * 开奖<b>按奖级升序逐级认领，一张票只中最高的那一级</b>
 * （见 {@link TicketMatcher} 与 {@code LotteryRecordDao.claimByRule} 的 {@code win_status=0} 守卫）。
 * 所以二等奖的实际中奖数不是「满足二等奖条件的票数」，而要扣掉已经被一等奖认领走的那部分。
 *
 * <p>多条规则同时命中的概率同样可精确算：每条规则锁死的是号码的一个前缀段或后缀段，
 * 若干条规则的交集锁死的位数 = {@code min(L, 最长前缀 + 最长后缀)}
 * —— 前缀与后缀首尾相接、覆盖到全长时就是全号锁死。于是
 * <pre>
 *   P(规则集合 S 全部命中) = 10^-min(L, maxPrefix(S) + maxSuffix(S))
 * </pre>
 * 再套容斥原理即得净中奖率。
 *
 * <h3>算术为什么用 BigDecimal 而不是 double</h3>
 * 所有中间量都是 10 的负整数次幂，BigDecimal 下加减乘<b>完全精确</b>，
 * 不会出现 0.1+0.2 那类误差。而这些数字要直接乘上发行量与奖品价值算赔付成本，
 * 是拿给人看着做决策的，不该带浮点毛刺。
 *
 * <h3>⚠️ 与匹配语义的一致性</h3>
 * 本类对「一条规则算不算命中」的判据必须与 {@link TicketMatcher} 逐条对齐：
 * 长度非正、超过号码长度、规则值非法，在 TicketMatcher 里都是<b>判不中</b>（返回 false），
 * 这里对应命中率 0。两边一旦漂移，页面上算出来的预计赔付就会与真实开奖结果对不上。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
public final class PrizeRuleProbability {

    private PrizeRuleProbability() {
    }

    /**
     * 参与精确容斥的更高奖级数量上限。
     *
     * 容斥要枚举 2^n 个子集，n 大了会指数爆炸。真实配置里一个玩法的奖级个位数，
     * 16 已经是 65536 个子集、绰绰有余；真超了就返回 null 表示「算不了」，
     * 而不是硬算到把请求拖死 —— 页面对 null 会显示「-」，不会假装有答案。
     */
    private static final int MAX_EXACT_LEVELS = 16;

    private static final BigDecimal TEN = BigDecimal.TEN;

    /**
     * 一条规则锁死的位段。
     *
     * @param prefixLen 锁死的前缀位数
     * @param suffixLen 锁死的后缀位数
     * @param never     该规则永不命中（长度非法或规则值非法），此时前后缀无意义
     */
    public record RuleMask(int prefixLen, int suffixLen, boolean never) {

        /** 工厂名不能也叫 never —— 会与 record 自动生成的访问器 never() 撞签名 */
        static RuleMask neverMatch() {
            return new RuleMask(0, 0, true);
        }
    }

    /**
     * 把一条规则翻译成位段掩码。
     *
     * @param matchRule    匹配规则，null 表示规则值非法
     * @param matchLength  匹配长度
     * @param numberLength 号码长度
     */
    public static RuleMask toMask(MatchRuleEnum matchRule, Integer matchLength, Integer numberLength) {
        if (matchRule == null || numberLength == null || numberLength <= 0) {
            return RuleMask.neverMatch();
        }
        if (matchRule == MatchRuleEnum.EXACT) {
            // EXACT 锁死全长，与 matchLength 无关 —— 服务端保存时也是这么归一的
            return new RuleMask(numberLength, 0, false);
        }
        // 与 TicketMatcher.compareSegment 一致：长度非正或超过号码长度，判不中
        if (matchLength == null || matchLength <= 0 || matchLength > numberLength) {
            return RuleMask.neverMatch();
        }
        return matchRule == MatchRuleEnum.HEAD
                ? new RuleMask(matchLength, 0, false)
                : new RuleMask(0, matchLength, false);
    }

    /**
     * 单条规则的命中率（不考虑更高奖级的抢占）。
     */
    public static BigDecimal hitRate(RuleMask mask, int numberLength) {
        if (mask == null || mask.never()) {
            return BigDecimal.ZERO;
        }
        return pow10Negative(covered(List.of(mask), numberLength));
    }

    /**
     * 净中奖率：扣掉被更高奖级抢先认领的部分。
     *
     * @param masks        全部规则的掩码，<b>必须已按 prizeLevel 升序排列</b> ——
     *                     顺序就是认领顺序，换了顺序结果就变了
     * @param index        要算第几条（0 基）
     * @param numberLength 号码长度
     * @return 净中奖率；更高奖级过多无法精确计算时返回 null
     */
    public static BigDecimal netRate(List<RuleMask> masks, int index, int numberLength) {
        RuleMask self = masks.get(index);
        if (self.never()) {
            return BigDecimal.ZERO;
        }
        // 只有「能命中」的更高奖级才会抢走票，永不命中的那些不参与
        List<RuleMask> higher = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            if (!masks.get(i).never()) {
                higher.add(masks.get(i));
            }
        }
        if (higher.size() > MAX_EXACT_LEVELS) {
            return null;
        }

        BigDecimal self0 = pow10Negative(covered(List.of(self), numberLength));
        if (higher.isEmpty()) {
            return self0;
        }

        /*
         * 容斥：P(自己 ∩ 任一更高奖级) = Σ_{非空子集T} (-1)^(|T|+1) · P(自己 ∩ ∩T)
         * 净中奖率 = P(自己) − 上式
         */
        BigDecimal overlap = BigDecimal.ZERO;
        int n = higher.size();
        for (int bits = 1; bits < (1 << n); bits++) {
            List<RuleMask> subset = new ArrayList<>();
            subset.add(self);
            int size = 0;
            for (int i = 0; i < n; i++) {
                if ((bits & (1 << i)) != 0) {
                    subset.add(higher.get(i));
                    size++;
                }
            }
            BigDecimal term = pow10Negative(covered(subset, numberLength));
            overlap = (size % 2 == 1) ? overlap.add(term) : overlap.subtract(term);
        }
        BigDecimal net = self0.subtract(overlap);
        // 容斥结果理论上不会为负；真为负说明掩码模型与实际规则语义脱节，宁可归零也不吐负数
        return net.signum() < 0 ? BigDecimal.ZERO : net;
    }

    /**
     * 一组规则同时命中所锁死的位数：最长前缀 + 最长后缀，首尾相接封顶到全长。
     */
    private static int covered(List<RuleMask> masks, int numberLength) {
        int maxPrefix = 0;
        int maxSuffix = 0;
        for (RuleMask mask : masks) {
            maxPrefix = Math.max(maxPrefix, mask.prefixLen());
            maxSuffix = Math.max(maxSuffix, mask.suffixLen());
        }
        return Math.min(numberLength, maxPrefix + maxSuffix);
    }

    /**
     * 10^-n，精确表示。n=0 时为 1（无任何约束，必中）。
     */
    private static BigDecimal pow10Negative(int n) {
        return BigDecimal.ONE.divide(TEN.pow(n));
    }

}
