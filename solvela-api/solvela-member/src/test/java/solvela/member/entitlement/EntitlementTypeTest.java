package solvela.member.entitlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 周期键：<b>幂等键的一半</b>。
 *
 * <h3>🔴 两种类型的周期键长度不同，这是刻意的</h3>
 * 唯一键是 {@code (member_id, entitlement_id, period_key)}。
 * 生日礼的周期键若用了 {@code yyyyMM}，唯一键<b>仍然生效</b> ——
 * 只是「一个周期」的含义从一年悄悄变成一个月，
 * 于是<b>生日礼一年能领十二次</b>。这个错不抛异常、不留日志，
 * 只有对预算时才会发现多花了十一倍的钱。
 *
 * @Date 2026-09-22
 */
class EntitlementTypeTest {

    @Test
    @DisplayName("生日礼按年：同一年的任何一天都是同一个周期")
    void 生日礼一年一次() {
        assertEquals("2026", EntitlementType.periodKeyOf(
                EntitlementType.BIRTHDAY, LocalDate.of(2026, 1, 1)));
        assertEquals("2026", EntitlementType.periodKeyOf(
                EntitlementType.BIRTHDAY, LocalDate.of(2026, 12, 31)));
    }

    @Test
    @DisplayName("月度券按月：同一月的任何一天都是同一个周期")
    void 月度券一月一次() {
        assertEquals("202609", EntitlementType.periodKeyOf(
                EntitlementType.MONTHLY, LocalDate.of(2026, 9, 1)));
        assertEquals("202609", EntitlementType.periodKeyOf(
                EntitlementType.MONTHLY, LocalDate.of(2026, 9, 30)));
    }

    @Test
    @DisplayName("🔴 两种类型的周期键必须不同口径 —— 混用会让生日礼一年发十二次")
    void 两种周期键口径不同() {
        LocalDate day = LocalDate.of(2026, 9, 22);
        assertNotEquals(
                EntitlementType.periodKeyOf(EntitlementType.BIRTHDAY, day),
                EntitlementType.periodKeyOf(EntitlementType.MONTHLY, day));
        assertEquals(4, EntitlementType.periodKeyOf(EntitlementType.BIRTHDAY, day).length());
        assertEquals(6, EntitlementType.periodKeyOf(EntitlementType.MONTHLY, day).length());
    }

    @Test
    @DisplayName("跨月/跨年真的换周期，否则「一月一次」就变成「一次就没了」")
    void 跨周期会换键() {
        assertNotEquals(
                EntitlementType.periodKeyOf(EntitlementType.MONTHLY, LocalDate.of(2026, 9, 30)),
                EntitlementType.periodKeyOf(EntitlementType.MONTHLY, LocalDate.of(2026, 10, 1)));
        assertNotEquals(
                EntitlementType.periodKeyOf(EntitlementType.BIRTHDAY, LocalDate.of(2026, 12, 31)),
                EntitlementType.periodKeyOf(EntitlementType.BIRTHDAY, LocalDate.of(2027, 1, 1)));
    }

    @Test
    @DisplayName("不认识的类型返回 null，不猜一个 —— 猜错方向是一年发十二次")
    void 未知类型不猜() {
        assertNull(EntitlementType.periodKeyOf("WEEKLY", LocalDate.of(2026, 9, 22)));
        assertNull(EntitlementType.periodKeyOf(null, LocalDate.of(2026, 9, 22)));
        assertFalse(EntitlementType.isKnown("WEEKLY"));
        assertTrue(EntitlementType.isKnown(EntitlementType.BIRTHDAY));
        assertTrue(EntitlementType.isKnown(EntitlementType.MONTHLY));
    }

    @Test
    @DisplayName("日期为 null 不抛，返回 null")
    void 日期为空() {
        assertNull(EntitlementType.periodKeyOf(EntitlementType.BIRTHDAY, null));
    }
}
