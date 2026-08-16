package sa.lottery.engine;

import sa.lottery.engine.PrizeRuleProbability.RuleMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 奖级中奖概率模型 单元测试（纯内存）。
 *
 * <p>最后一组「与 TicketMatcher 穷举对拍」是本测试的重点：
 * 概率模型与真实匹配语义是同一规则的两种实现，
 * 就像 {@code TicketMatcher} 与开奖 SQL 的关系一样，必须防漂移。
 * 对拍直接穷举全部号码空间，算出真实的净中奖比例，与公式结果逐位比对。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
class PrizeRuleProbabilityTest {

    private static RuleMask mask(MatchRuleEnum rule, Integer length, int numberLength) {
        return PrizeRuleProbability.toMask(rule, length, numberLength);
    }

    private static BigDecimal rate(String value) {
        return new BigDecimal(value);
    }

    private static void assertRate(String expected, BigDecimal actual) {
        assertEquals(0, rate(expected).compareTo(actual),
                () -> "期望 " + expected + " 实际 " + actual);
    }

    // ==================== 单条命中率 ====================

    @Test
    @DisplayName("单条命中率：TAIL n / HEAD n 都是 10^-n，与号码长度无关")
    void hitRateOfSegmentRules() {
        assertRate("0.1", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.TAIL, 1, 5), 5));
        assertRate("0.001", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.TAIL, 3, 5), 5));
        assertRate("0.01", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.HEAD, 2, 7), 7));
    }

    @Test
    @DisplayName("单条命中率：EXACT 锁死全长，等于 10^-L，与传入的匹配长度无关")
    void hitRateOfExact() {
        assertRate("0.00001", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.EXACT, 3, 5), 5));
        assertRate("0.0000001", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.EXACT, 99, 7), 7));
    }

    @Test
    @DisplayName("永不命中的规则命中率为 0：长度非正、超过号码长度、规则值非法")
    void hitRateOfDeadRules() {
        assertRate("0", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.TAIL, 0, 5), 5));
        assertRate("0", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.TAIL, -1, 5), 5));
        assertRate("0", PrizeRuleProbability.hitRate(mask(MatchRuleEnum.TAIL, 6, 5), 5));
        assertRate("0", PrizeRuleProbability.hitRate(mask(null, 3, 5), 5));
    }

    // ==================== 净中奖率 ====================

    @Test
    @DisplayName("净中奖率：典型三奖级，逐级扣掉被更高奖级抢走的部分")
    void netRateOfStandardLadder() {
        // 一等奖 EXACT/5、二等奖 TAIL/3、三等奖 TAIL/1
        List<RuleMask> masks = List.of(
                mask(MatchRuleEnum.EXACT, 5, 5),
                mask(MatchRuleEnum.TAIL, 3, 5),
                mask(MatchRuleEnum.TAIL, 1, 5));

        assertRate("0.00001", PrizeRuleProbability.netRate(masks, 0, 5));
        // 0.001 - 0.00001
        assertRate("0.00099", PrizeRuleProbability.netRate(masks, 1, 5));
        // 0.1 - 0.001
        assertRate("0.099", PrizeRuleProbability.netRate(masks, 2, 5));
    }

    @Test
    @DisplayName("净中奖率为 0 = 该奖级永远认领不到票（线上真实存在过的坏配置）")
    void netRateZeroMeansUnreachable() {
        // 两条规则的匹配条件完全相同，低奖级永远抢不到
        List<RuleMask> masks = List.of(
                mask(MatchRuleEnum.TAIL, 1, 5),
                mask(MatchRuleEnum.TAIL, 1, 5));

        assertRate("0.1", PrizeRuleProbability.netRate(masks, 0, 5));
        assertRate("0", PrizeRuleProbability.netRate(masks, 1, 5));
    }

    @Test
    @DisplayName("净中奖率：更宽的规则排在更高奖级时，低奖级同样被完全吃掉")
    void netRateWiderRuleAtHigherLevel() {
        // 一等奖尾1（很宽），二等奖尾3（更窄）—— 尾3必然也满足尾1，被一等奖全部取走
        List<RuleMask> masks = List.of(
                mask(MatchRuleEnum.TAIL, 1, 5),
                mask(MatchRuleEnum.TAIL, 3, 5));

        assertRate("0.1", PrizeRuleProbability.netRate(masks, 0, 5));
        assertRate("0", PrizeRuleProbability.netRate(masks, 1, 5));
    }

    @Test
    @DisplayName("HEAD 与 TAIL 相互独立：交集概率是 10^-(a+b)")
    void netRateHeadTailIndependent() {
        // 一等奖 HEAD/2，二等奖 TAIL/2，号码长 5：交集 = 前2位且后2位 = 10^-4
        List<RuleMask> masks = List.of(
                mask(MatchRuleEnum.HEAD, 2, 5),
                mask(MatchRuleEnum.TAIL, 2, 5));

        assertRate("0.01", PrizeRuleProbability.netRate(masks, 0, 5));
        // 0.01 - 0.0001
        assertRate("0.0099", PrizeRuleProbability.netRate(masks, 1, 5));
    }

    @Test
    @DisplayName("HEAD + TAIL 覆盖到全长时交集退化为全号匹配，不会算成 10^-(a+b)")
    void netRateHeadTailOverlapping() {
        // 号码长 5，HEAD/3 与 TAIL/3 首尾相接超过全长，交集就是全号 = 10^-5 而非 10^-6
        List<RuleMask> masks = List.of(
                mask(MatchRuleEnum.HEAD, 3, 5),
                mask(MatchRuleEnum.TAIL, 3, 5));

        assertRate("0.001", PrizeRuleProbability.netRate(masks, 0, 5));
        // 0.001 - 0.00001（不是 0.001 - 0.000001）
        assertRate("0.00099", PrizeRuleProbability.netRate(masks, 1, 5));
    }

    @Test
    @DisplayName("永不命中的更高奖级不参与抢占，不该压低后面奖级的净中奖率")
    void deadHigherLevelDoesNotSteal() {
        List<RuleMask> masks = List.of(
                mask(MatchRuleEnum.TAIL, 9, 5),   // 超长，永不命中
                mask(MatchRuleEnum.TAIL, 1, 5));

        assertRate("0", PrizeRuleProbability.netRate(masks, 0, 5));
        assertRate("0.1", PrizeRuleProbability.netRate(masks, 1, 5));
    }

    @Test
    @DisplayName("奖级过多时返回 null 而不是硬算：页面显示「-」，不假装有答案")
    void tooManyLevelsReturnsNull() {
        List<RuleMask> masks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            masks.add(mask(MatchRuleEnum.HEAD, 1, 9));
        }
        assertNull(PrizeRuleProbability.netRate(masks, 19, 9));
    }

    // ==================== 与 TicketMatcher 穷举对拍 ====================

    @Test
    @DisplayName("对拍：4 位号码穷举全部 1 万个号码，实测净中奖比例与公式完全一致")
    void bruteForceAgainstTicketMatcher() {
        int length = 4;
        List<PrizeRuleSnapshot> rules = List.of(
                new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 4, "P1"),
                new PrizeRuleSnapshot(2, MatchRuleEnum.HEAD, 2, "P2"),
                new PrizeRuleSnapshot(3, MatchRuleEnum.TAIL, 2, "P3"),
                new PrizeRuleSnapshot(4, MatchRuleEnum.TAIL, 1, "P4"));
        List<RuleMask> masks = rules.stream()
                .map(r -> mask(r.matchRule(), r.matchLength(), length)).toList();

        String winning = "7391";
        int[] hits = new int[rules.size()];
        int domain = (int) Math.pow(10, length);
        for (int i = 0; i < domain; i++) {
            String ticket = String.format("%0" + length + "d", i);
            PrizeRuleSnapshot won = TicketMatcher.match(ticket, winning, rules);
            if (won != null) {
                hits[won.prizeLevel() - 1]++;
            }
        }

        for (int i = 0; i < rules.size(); i++) {
            BigDecimal expected = BigDecimal.valueOf(hits[i]).divide(BigDecimal.valueOf(domain));
            BigDecimal actual = PrizeRuleProbability.netRate(masks, i, length);
            int level = i + 1;
            assertEquals(0, expected.compareTo(actual),
                    () -> "奖级 " + level + " 实测 " + expected + " 公式 " + actual);
        }
    }

    @Test
    @DisplayName("对拍：随机开奖号码与随机规则组合，实测比例始终等于公式")
    void bruteForceRandomised() {
        Random random = new Random(20260815L);
        int length = 4;
        int domain = (int) Math.pow(10, length);

        for (int round = 0; round < 30; round++) {
            int ruleCount = 1 + random.nextInt(4);
            List<PrizeRuleSnapshot> rules = new ArrayList<>();
            for (int i = 0; i < ruleCount; i++) {
                MatchRuleEnum rule = MatchRuleEnum.values()[random.nextInt(MatchRuleEnum.values().length)];
                rules.add(new PrizeRuleSnapshot(i + 1, rule, 1 + random.nextInt(length), "P" + i));
            }
            List<RuleMask> masks = rules.stream()
                    .map(r -> mask(r.matchRule(), r.matchLength(), length)).toList();
            String winning = String.format("%0" + length + "d", random.nextInt(domain));

            int[] hits = new int[ruleCount];
            for (int i = 0; i < domain; i++) {
                String ticket = String.format("%0" + length + "d", i);
                PrizeRuleSnapshot won = TicketMatcher.match(ticket, winning, rules);
                if (won != null) {
                    hits[won.prizeLevel() - 1]++;
                }
            }
            for (int i = 0; i < ruleCount; i++) {
                BigDecimal expected = BigDecimal.valueOf(hits[i]).divide(BigDecimal.valueOf(domain));
                BigDecimal actual = PrizeRuleProbability.netRate(masks, i, length);
                int level = i + 1;
                int finalRound = round;
                assertEquals(0, expected.compareTo(actual),
                        () -> "第 " + finalRound + " 轮 奖级 " + level
                                + " 开奖号 " + winning + " 实测 " + expected + " 公式 " + actual);
            }
        }
    }

    @Test
    @DisplayName("对拍：各奖级净中奖率之和 = 实测总中奖比例")
    void totalRateMatchesBruteForce() {
        int length = 4;
        int domain = (int) Math.pow(10, length);
        List<PrizeRuleSnapshot> rules = List.of(
                new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 4, "P1"),
                new PrizeRuleSnapshot(2, MatchRuleEnum.TAIL, 2, "P2"),
                new PrizeRuleSnapshot(3, MatchRuleEnum.HEAD, 1, "P3"));
        List<RuleMask> masks = rules.stream()
                .map(r -> mask(r.matchRule(), r.matchLength(), length)).toList();

        String winning = "0000";
        int wins = 0;
        for (int i = 0; i < domain; i++) {
            String ticket = String.format("%0" + length + "d", i);
            if (TicketMatcher.match(ticket, winning, rules) != null) {
                wins++;
            }
        }
        BigDecimal formulaTotal = BigDecimal.ZERO;
        for (int i = 0; i < rules.size(); i++) {
            formulaTotal = formulaTotal.add(PrizeRuleProbability.netRate(masks, i, length));
        }
        BigDecimal measured = BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(domain));
        assertEquals(0, measured.compareTo(formulaTotal),
                "实测总中奖率 " + measured + " 公式合计 " + formulaTotal);
        assertTrue(formulaTotal.compareTo(BigDecimal.ONE) <= 0, "总中奖率不可能超过 1");
    }
}
