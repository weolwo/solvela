package sa.lottery.settle;

import sa.lottery.engine.MatchRuleEnum;
import sa.lottery.engine.PrizeRuleSnapshot;
import sa.lottery.engine.TicketMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核销语义测试：用「模拟 SQL」的方式，验证集合式核销与 {@link TicketMatcher} 判定一致。
 *
 * <p><b>为什么需要这个测试</b>：真正的核销走的是 SQL
 * （{@code UPDATE ... WHERE win_status=0 AND RIGHT(ticket_number,n)=...}，按奖级升序逐条执行），
 * 而 {@link TicketMatcher} 是匹配语义的权威定义。两者是同一套规则的两种实现，
 * <b>一旦漂移，线上开奖结果就会与单测结论背离</b>，而且不会有任何报错。
 *
 * <p>这里在内存里复刻 SQL 的执行方式（逐条规则扫全表、只认领尚未判定的、命中就改状态），
 * 再与 TicketMatcher 逐票比对。它锁不住 SQL 本身写错，但能锁住
 * 「奖级互斥」「命中即止」「字符串比较而非数值比较」这几条最容易写反的语义。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
class SettleSemanticsTest {

    /**
     * 一张票的核销态，对应 t_lottery_record 的三个字段
     */
    private static final class Ticket {
        final String number;
        int winStatus = 0;
        Integer prizeLevel;
        String prizeCode;

        Ticket(String number) {
            this.number = number;
        }
    }

    /**
     * 复刻 claimByRule + markNoWin 的集合式执行：
     * 按奖级升序逐条规则扫描，只认领 winStatus==0 的票；全部规则跑完后剩下的判未中奖
     */
    private void settleLikeSql(List<Ticket> tickets, List<PrizeRuleSnapshot> rules, String winningNumber) {
        rules.stream()
                .sorted((a, b) -> Integer.compare(a.prizeLevel(), b.prizeLevel()))
                .forEach(rule -> {
                    for (Ticket t : tickets) {
                        // 这一句就是 SQL 里的 AND win_status = 0 —— 奖级互斥的实现
                        if (t.winStatus != 0) {
                            continue;
                        }
                        if (matchesLikeSql(rule, t.number, winningNumber)) {
                            t.winStatus = 2;
                            t.prizeLevel = rule.prizeLevel();
                            t.prizeCode = rule.prizeCode();
                        }
                    }
                });
        for (Ticket t : tickets) {
            if (t.winStatus == 0) {
                t.winStatus = 1;
                t.prizeLevel = 99;
            }
        }
    }

    /**
     * 复刻 SQL 的三个分支：EXACT 用 =，TAIL 用 RIGHT，HEAD 用 LEFT，一律字符串比较
     */
    private boolean matchesLikeSql(PrizeRuleSnapshot rule, String ticket, String winning) {
        int len = rule.matchLength();
        return switch (rule.matchRule()) {
            case EXACT -> ticket.equals(winning);
            case TAIL -> len <= ticket.length() && len <= winning.length()
                    && ticket.substring(ticket.length() - len).equals(winning.substring(winning.length() - len));
            case HEAD -> len <= ticket.length() && len <= winning.length()
                    && ticket.substring(0, len).equals(winning.substring(0, len));
        };
    }

    private List<PrizeRuleSnapshot> standardRules() {
        return List.of(
                new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 5, "P_IPHONE"),
                new PrizeRuleSnapshot(2, MatchRuleEnum.TAIL, 3, "P_CASH"),
                new PrizeRuleSnapshot(3, MatchRuleEnum.TAIL, 1, "P_COUPON")
        );
    }

    @Test
    @DisplayName("★ 集合式核销与 TicketMatcher 判定必须逐票一致（全域 10 万张）")
    void sqlSemanticsMatchesEngine() {
        String winning = "89102";
        List<PrizeRuleSnapshot> rules = standardRules();

        List<Ticket> tickets = new ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++) {
            tickets.add(new Ticket(String.format("%05d", i)));
        }

        settleLikeSql(tickets, rules, winning);

        int mismatch = 0;
        for (Ticket t : tickets) {
            PrizeRuleSnapshot expected = TicketMatcher.match(t.number, winning, rules);
            if (expected == null) {
                if (t.winStatus != 1 || t.prizeLevel != 99) {
                    mismatch++;
                }
            } else if (t.winStatus != 2 || t.prizeLevel == null || expected.prizeLevel() != t.prizeLevel
                    || !expected.prizeCode().equals(t.prizeCode)) {
                mismatch++;
            }
        }
        assertEquals(0, mismatch, "集合式核销与 TicketMatcher 判定不一致的票数");
    }

    @Test
    @DisplayName("奖级互斥：中一等奖的票不会被二等奖再认领一次")
    void higherLevelClaimsFirst() {
        String winning = "89102";
        List<Ticket> tickets = List.of(new Ticket("89102"), new Ticket("55102"), new Ticket("55552"), new Ticket("55555"))
                .stream().collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        settleLikeSql(tickets, standardRules(), winning);

        assertEquals(1, tickets.get(0).prizeLevel, "全号命中应为一等奖");
        assertEquals(2, tickets.get(1).prizeLevel, "尾3命中应为二等奖");
        assertEquals(3, tickets.get(2).prizeLevel, "尾1命中应为三等奖");
        assertEquals(99, tickets.get(3).prizeLevel, "未命中应落 99");
        assertEquals(2, tickets.get(0).winStatus);
        assertEquals(1, tickets.get(3).winStatus);
    }

    @Test
    @DisplayName("字符串比较而非数值比较：前导零不能被截断")
    void stringComparisonNotNumeric() {
        // 09102 与 89102 的尾 3 位都是 102，应中二等奖；但若按数值比较会得出不同结果
        List<Ticket> tickets = new ArrayList<>(List.of(new Ticket("09102")));
        settleLikeSql(tickets, standardRules(), "89102");
        assertEquals(2, tickets.get(0).prizeLevel);

        // 全号匹配下 09102 != 91020，不能因为数值近似就误判
        List<Ticket> exactCase = new ArrayList<>(List.of(new Ticket("09102")));
        settleLikeSql(exactCase, List.of(new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 5, "P")), "91020");
        assertEquals(99, exactCase.get(0).prizeLevel);
    }

    @Test
    @DisplayName("重复核销幂等：第二次执行不会改变任何结果（win_status=0 的守卫）")
    void settleIsIdempotent() {
        String winning = "89102";
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tickets.add(new Ticket(String.format("%05d", i)));
        }
        settleLikeSql(tickets, standardRules(), winning);
        List<Integer> first = tickets.stream().map(t -> t.prizeLevel).toList();

        settleLikeSql(tickets, standardRules(), winning);
        List<Integer> second = tickets.stream().map(t -> t.prizeLevel).toList();

        assertEquals(first, second, "重跑核销不应改变已判定的结果");
    }

    @Test
    @DisplayName("中奖数符合概率预期：全号 1 张、尾3 约 100 张、尾1 约 10000 张")
    void winCountsMatchExpectation() {
        String winning = "89102";
        List<Ticket> tickets = new ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++) {
            tickets.add(new Ticket(String.format("%05d", i)));
        }
        settleLikeSql(tickets, standardRules(), winning);

        long lv1 = tickets.stream().filter(t -> Integer.valueOf(1).equals(t.prizeLevel)).count();
        long lv2 = tickets.stream().filter(t -> Integer.valueOf(2).equals(t.prizeLevel)).count();
        long lv3 = tickets.stream().filter(t -> Integer.valueOf(3).equals(t.prizeLevel)).count();
        long lose = tickets.stream().filter(t -> Integer.valueOf(99).equals(t.prizeLevel)).count();

        assertEquals(1, lv1, "全号命中恰好 1 张");
        // 尾3 共 100 张，其中 1 张已被一等奖认走
        assertEquals(99, lv2, "尾3 命中 100 张，减去被一等奖认走的 1 张");
        // 尾1 共 10000 张，减去被前两级认走的 100 张
        assertEquals(9_900, lv3, "尾1 命中 10000 张，减去被前两级认走的 100 张");
        assertEquals(100_000, lv1 + lv2 + lv3 + lose, "所有票都必须有终态，不能有遗漏");
        assertTrue(tickets.stream().noneMatch(t -> t.winStatus == 0), "不能有票停在未判定态");
    }
}
