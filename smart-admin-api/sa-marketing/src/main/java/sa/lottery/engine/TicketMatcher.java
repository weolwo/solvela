package sa.lottery.engine;

import java.util.Comparator;
import java.util.List;

/**
 * 号码与奖级的匹配判定（纯函数，无状态）。
 *
 * <h3>奖级互斥：一张票只中一次，取最高奖级</h3>
 * 一张中了一等奖（全号相同）的票，必然也满足二等奖（尾 3 位相同）——
 * 所以判定必须<b>按 prizeLevel 升序逐级认领，命中即止</b>，否则同一张票会被算成中了多个奖。
 *
 * <h3>⚠️ 本类是匹配语义的唯一权威定义，SQL 必须与它保持一致</h3>
 * 真正的开奖核销走集合式 SQL（10 万行不可能捞进 Java 循环），形如：
 * <pre>
 * UPDATE t_lottery_record SET win_status=2, prize_level=?, prize_code=?
 *  WHERE lottery_code=? AND issue_no=? AND win_status=0
 *    AND RIGHT(ticket_number, ?) = ?          -- TAIL；HEAD 用 LEFT，EXACT 用 ticket_number=?
 * </pre>
 * 那里的 {@code win_status=0} 守卫 + 按 prizeLevel 升序执行，正是本类「命中即止」的集合式等价写法。
 * <b>两处一旦不一致，线上开奖结果就会与单测结论背离</b>——改任何一边都要同步另一边，
 * 单测里的边界用例（尤其 EXACT 与满长 TAIL 等价）就是防这个的。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
public final class TicketMatcher {

    private TicketMatcher() {
    }

    /**
     * 判定一张号码中了哪个奖级
     *
     * @param ticketNumber  用户号码
     * @param winningNumber 开奖号码
     * @param rules         奖级规则，顺序无所谓，内部会按 prizeLevel 升序排
     * @return 命中的最高奖级；未中奖返回 null
     */
    public static PrizeRuleSnapshot match(String ticketNumber, String winningNumber, List<PrizeRuleSnapshot> rules) {
        if (ticketNumber == null || winningNumber == null || rules == null || rules.isEmpty()) {
            return null;
        }
        return rules.stream()
                .sorted(Comparator.comparingInt(PrizeRuleSnapshot::prizeLevel))
                .filter(rule -> matches(rule, ticketNumber, winningNumber))
                .findFirst()
                .orElse(null);
    }

    /**
     * 单条规则是否命中。长度不足以支撑 matchLength 时判不中，而不是抛异常——
     * 开奖是批量动作，一条脏规则不该让整期核销失败；规则合法性在配置保存时把关。
     */
    public static boolean matches(PrizeRuleSnapshot rule, String ticketNumber, String winningNumber) {
        if (rule == null || ticketNumber == null || winningNumber == null) {
            return false;
        }
        return switch (rule.matchRule()) {
            case EXACT -> ticketNumber.equals(winningNumber);
            case TAIL -> compareSegment(ticketNumber, winningNumber, rule.matchLength(), false);
            case HEAD -> compareSegment(ticketNumber, winningNumber, rule.matchLength(), true);
        };
    }

    /**
     * 比较首/尾 length 位
     *
     * @param head true 比首段（对应 SQL 的 LEFT），false 比尾段（对应 SQL 的 RIGHT）
     */
    private static boolean compareSegment(String ticketNumber, String winningNumber, int length, boolean head) {
        if (length <= 0 || ticketNumber.length() < length || winningNumber.length() < length) {
            return false;
        }
        if (head) {
            return ticketNumber.regionMatches(0, winningNumber, 0, length);
        }
        return ticketNumber.regionMatches(ticketNumber.length() - length,
                winningNumber, winningNumber.length() - length, length);
    }
}
