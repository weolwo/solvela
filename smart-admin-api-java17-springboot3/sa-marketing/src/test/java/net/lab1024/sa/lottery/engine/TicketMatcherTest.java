package net.lab1024.sa.lottery.engine;

import net.lab1024.sa.lottery.constant.LotteryConst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 号码匹配判定 单元测试（纯内存）
 *
 * @Author alaric
 * @Date 2026-07-27
 */
class TicketMatcherTest {

    private static final String WINNING = "89102";

    /**
     * 典型三奖级：一等奖全号、二等奖尾3、三等奖尾1
     */
    private List<PrizeRuleSnapshot> standardRules() {
        return List.of(
                new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 5, "PRIZE_IPHONE"),
                new PrizeRuleSnapshot(2, MatchRuleEnum.TAIL, 3, "PRIZE_SCORE"),
                new PrizeRuleSnapshot(3, MatchRuleEnum.TAIL, 1, "PRIZE_COUPON")
        );
    }

    // ==================== 奖级互斥 ====================

    @Test
    @DisplayName("奖级互斥：全号命中的票同时满足尾3和尾1，但只能中最高的一等奖")
    void highestLevelWins() {
        PrizeRuleSnapshot hit = TicketMatcher.match(WINNING, WINNING, standardRules());
        assertNotNull(hit);
        assertEquals(1, hit.prizeLevel());
        assertEquals("PRIZE_IPHONE", hit.prizeCode());

        // 反证：单独看，低奖级规则确实也命中了 —— 所以「命中即止」不可省
        assertTrue(TicketMatcher.matches(standardRules().get(1), WINNING, WINNING));
        assertTrue(TicketMatcher.matches(standardRules().get(2), WINNING, WINNING));
    }

    @Test
    @DisplayName("规则乱序传入也按奖级升序判定，不依赖调用方排序")
    void orderIndependent() {
        List<PrizeRuleSnapshot> shuffled = List.of(
                new PrizeRuleSnapshot(3, MatchRuleEnum.TAIL, 1, "PRIZE_COUPON"),
                new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 5, "PRIZE_IPHONE"),
                new PrizeRuleSnapshot(2, MatchRuleEnum.TAIL, 3, "PRIZE_SCORE")
        );
        assertEquals(1, TicketMatcher.match(WINNING, WINNING, shuffled).prizeLevel());
    }

    @Test
    @DisplayName("只中尾3时落二等奖；只中尾1时落三等奖；都不中返回 null")
    void levelByLevel() {
        assertEquals(2, TicketMatcher.match("55102", WINNING, standardRules()).prizeLevel());
        assertEquals(3, TicketMatcher.match("55552", WINNING, standardRules()).prizeLevel());
        assertNull(TicketMatcher.match("55555", WINNING, standardRules()));
    }

    // ==================== 三种匹配规则 ====================

    @Test
    @DisplayName("HEAD 比首段、TAIL 比尾段，方向不能反")
    void headAndTailDirection() {
        PrizeRuleSnapshot head2 = new PrizeRuleSnapshot(1, MatchRuleEnum.HEAD, 2, "P");
        PrizeRuleSnapshot tail2 = new PrizeRuleSnapshot(1, MatchRuleEnum.TAIL, 2, "P");

        // 89102：首2=89，尾2=02
        assertTrue(TicketMatcher.matches(head2, "89555", WINNING));
        assertFalse(TicketMatcher.matches(head2, "55502", WINNING));

        assertTrue(TicketMatcher.matches(tail2, "55502", WINNING));
        assertFalse(TicketMatcher.matches(tail2, "89555", WINNING));
    }

    @Test
    @DisplayName("EXACT 与「满长 TAIL/HEAD」语义等价 —— SQL 侧改写时最容易踩错的一处")
    void exactEqualsFullLengthSegment() {
        PrizeRuleSnapshot exact = new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 5, "P");
        PrizeRuleSnapshot fullTail = new PrizeRuleSnapshot(1, MatchRuleEnum.TAIL, 5, "P");
        PrizeRuleSnapshot fullHead = new PrizeRuleSnapshot(1, MatchRuleEnum.HEAD, 5, "P");

        for (String ticket : List.of(WINNING, "89103", "09102", "00000")) {
            boolean expected = ticket.equals(WINNING);
            assertEquals(expected, TicketMatcher.matches(exact, ticket, WINNING), ticket);
            assertEquals(expected, TicketMatcher.matches(fullTail, ticket, WINNING), ticket);
            assertEquals(expected, TicketMatcher.matches(fullHead, ticket, WINNING), ticket);
        }
    }

    @Test
    @DisplayName("前导零不被当成数字截断：字符串比对，09102 与 9102 不是一回事")
    void leadingZeroIsSignificant() {
        PrizeRuleSnapshot exact = new PrizeRuleSnapshot(1, MatchRuleEnum.EXACT, 5, "P");
        assertTrue(TicketMatcher.matches(exact, "09102", "09102"));
        assertFalse(TicketMatcher.matches(exact, "09102", "91020"));
    }

    // ==================== 边界与脏数据 ====================

    @Test
    @DisplayName("匹配长度超过号码长度时判不中，而不是抛异常：一条脏规则不该让整期核销失败")
    void oversizedMatchLengthIsMiss() {
        PrizeRuleSnapshot tooLong = new PrizeRuleSnapshot(1, MatchRuleEnum.TAIL, 8, "P");
        assertFalse(TicketMatcher.matches(tooLong, WINNING, WINNING));
        assertNull(TicketMatcher.match(WINNING, WINNING, List.of(tooLong)));
    }

    @Test
    @DisplayName("空规则表 / null 入参一律返回未中奖，不抛异常")
    void nullSafe() {
        assertNull(TicketMatcher.match(WINNING, WINNING, List.of()));
        assertNull(TicketMatcher.match(WINNING, WINNING, null));
        assertNull(TicketMatcher.match(null, WINNING, standardRules()));
        assertNull(TicketMatcher.match(WINNING, null, standardRules()));
        assertFalse(TicketMatcher.matches(null, WINNING, WINNING));
    }

    // ==================== 规则自身的合法性 ====================

    @Test
    @DisplayName("奖级 99 被拒：它是「未中奖」的占位值，放行会让 C 端无法区分")
    void rejectReservedPrizeLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrizeRuleSnapshot(LotteryConst.PRIZE_LEVEL_NONE, MatchRuleEnum.TAIL, 1, "P"));
    }

    @Test
    @DisplayName("奖级从 1 起、匹配长度大于 0、必须绑定奖品")
    void rejectMalformedRule() {
        assertThrows(IllegalArgumentException.class, () -> new PrizeRuleSnapshot(0, MatchRuleEnum.TAIL, 1, "P"));
        assertThrows(IllegalArgumentException.class, () -> new PrizeRuleSnapshot(1, MatchRuleEnum.TAIL, 0, "P"));
        assertThrows(IllegalArgumentException.class, () -> new PrizeRuleSnapshot(1, null, 1, "P"));
        assertThrows(IllegalArgumentException.class, () -> new PrizeRuleSnapshot(1, MatchRuleEnum.TAIL, 1, " "));
    }

    // ==================== 枚举转换 ====================

    @Test
    @DisplayName("resolve 显式转枚举：大小写与空白容错，非法值返回 null 而不是抛异常")
    void resolveEnum() {
        assertEquals(MatchRuleEnum.EXACT, MatchRuleEnum.resolve("EXACT"));
        assertEquals(MatchRuleEnum.TAIL, MatchRuleEnum.resolve(" tail "));
        assertNull(MatchRuleEnum.resolve("SUFFIX"));
        assertNull(MatchRuleEnum.resolve(null));
    }

    @Test
    @DisplayName("getValue 必须返回枚举值本身：@CheckEnum 的白名单靠它，返 null 会让所有取值判非法")
    void getValueIsWiredCorrectly() {
        assertEquals("EXACT", MatchRuleEnum.EXACT.getValue());
        assertEquals("TAIL", MatchRuleEnum.TAIL.getValue());
        assertEquals("HEAD", MatchRuleEnum.HEAD.getValue());
    }
}
