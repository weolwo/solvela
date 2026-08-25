package solvela.draw.runtime;

import solvela.draw.runtime.DrawPeriodResolver.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 奖池重置周期解析 单元测试（纯内存）。
 *
 * <p>重点在边界：跨零点、跨周、跨月、跨年周。
 * 这些地方错了不会抛异常，只会让某个用户多抽一次或少抽一次 ——
 * 是那种上线几个月后才被客诉发现的缺陷，必须在这里钉死。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
class DrawPeriodResolverTest {

    private static Period day(LocalDateTime t) {
        return DrawPeriodResolver.resolve(DrawPeriodResolver.PERIOD_DAY, t);
    }

    private static Period week(LocalDateTime t) {
        return DrawPeriodResolver.resolve(DrawPeriodResolver.PERIOD_WEEK, t);
    }

    private static Period month(LocalDateTime t) {
        return DrawPeriodResolver.resolve(DrawPeriodResolver.PERIOD_MONTH, t);
    }

    // ==================== 每天 ====================

    @Test
    @DisplayName("每天：同一天内桶不变，跨零点即换桶")
    void dayBucket() {
        assertEquals("20260816", day(LocalDateTime.of(2026, 8, 16, 0, 0, 0)).bucket());
        assertEquals("20260816", day(LocalDateTime.of(2026, 8, 16, 23, 59, 59)).bucket());
        assertEquals("20260817", day(LocalDateTime.of(2026, 8, 17, 0, 0, 0)).bucket());
    }

    @Test
    @DisplayName("每天：23:59:59 与次日 00:00:00 必须落在不同桶（跨零点重置的核心）")
    void dayBoundary() {
        assertNotEquals(day(LocalDateTime.of(2026, 8, 16, 23, 59, 59)).bucket(),
                day(LocalDateTime.of(2026, 8, 17, 0, 0, 0)).bucket());
    }

    // ==================== 每周 ====================

    @Test
    @DisplayName("每周：周一到周日同桶，下周一换桶")
    void weekBucket() {
        // 2026-08-10 是周一，2026-08-16 是周日
        String monday = week(LocalDateTime.of(2026, 8, 10, 0, 0)).bucket();
        String sunday = week(LocalDateTime.of(2026, 8, 16, 23, 59, 59)).bucket();
        String nextMonday = week(LocalDateTime.of(2026, 8, 17, 0, 0)).bucket();

        assertEquals(monday, sunday, "同一 ISO 周内必须同桶");
        assertNotEquals(sunday, nextMonday, "跨周必须换桶");
    }

    @Test
    @DisplayName("每周：跨年周带 week-based-year，2026 年第1周与 2027 年第1周不能撞桶")
    void weekAcrossYear() {
        // 2026-12-31 属于 ISO 2026-W53；2027-01-01 属于 ISO 2026-W53（同周）
        // 而 2027 年真正的第 1 周要到 2027-01-04（周一）
        String w2026 = week(LocalDateTime.of(2026, 1, 1, 12, 0)).bucket();
        String w2027 = week(LocalDateTime.of(2027, 1, 4, 12, 0)).bucket();
        assertNotEquals(w2026, w2027, "不同年的第1周不能是同一个桶");
        assertTrue(w2026.contains("W"), "周桶应含 W 以便与日期桶区分：" + w2026);
        assertTrue(w2027.startsWith("2027"), "应带 week-based-year：" + w2027);
    }

    @Test
    @DisplayName("每周：周序号补零到两位，避免 2026W1 与 2026W10 之类的歧义")
    void weekZeroPadded() {
        String bucket = week(LocalDateTime.of(2026, 1, 8, 12, 0)).bucket();
        assertTrue(bucket.matches("\\d{4}W\\d{2}"), "格式应为 yyyyWww：" + bucket);
    }

    // ==================== 每月 ====================

    @Test
    @DisplayName("每月：月初到月末同桶，次月换桶")
    void monthBucket() {
        assertEquals("202608", month(LocalDateTime.of(2026, 8, 1, 0, 0)).bucket());
        assertEquals("202608", month(LocalDateTime.of(2026, 8, 31, 23, 59, 59)).bucket());
        assertEquals("202609", month(LocalDateTime.of(2026, 9, 1, 0, 0)).bucket());
    }

    @Test
    @DisplayName("每月：跨年不撞桶")
    void monthAcrossYear() {
        assertNotEquals(month(LocalDateTime.of(2026, 12, 31, 23, 0)).bucket(),
                month(LocalDateTime.of(2027, 1, 1, 1, 0)).bucket());
    }

    // ==================== 不重置 ====================

    @Test
    @DisplayName("ACTIVITY：恒为 ALL 桶且不过期，与改动前行为一致")
    void activityBucket() {
        Period p = DrawPeriodResolver.resolve(DrawPeriodResolver.PERIOD_ACTIVITY, LocalDateTime.now());
        assertEquals(DrawPeriodResolver.BUCKET_ALL, p.bucket());
        assertEquals(DrawPeriodResolver.TTL_NONE, p.ttlSeconds());
    }

    @Test
    @DisplayName("脏值/空值退回不重置，而不是按天重置 —— 宁可比配置严格，也不能让用户多抽")
    void unknownFallsBackToNoReset() {
        for (String bad : new String[]{null, "", "  ", "DAILY", "每天", "YEAR", "unknown"}) {
            Period p = DrawPeriodResolver.resolve(bad, LocalDateTime.of(2026, 8, 16, 12, 0));
            assertEquals(DrawPeriodResolver.BUCKET_ALL, p.bucket(), "脏值 [" + bad + "] 应退回不重置");
            assertEquals(DrawPeriodResolver.TTL_NONE, p.ttlSeconds());
        }
    }

    @Test
    @DisplayName("取值大小写与空格不敏感：配置里手写的 ' day ' 也要能识别")
    void caseInsensitive() {
        assertEquals("20260816", DrawPeriodResolver.resolve(" day ", LocalDateTime.of(2026, 8, 16, 9, 0)).bucket());
        assertEquals("202608", DrawPeriodResolver.resolve("Month", LocalDateTime.of(2026, 8, 16, 9, 0)).bucket());
    }

    // ==================== TTL ====================

    @Test
    @DisplayName("TTL 必须长于其周期本身，否则周期没到计数就被清空 = 超发")
    void ttlLongerThanPeriod() {
        assertTrue(day(LocalDateTime.now()).ttlSeconds() > 24 * 3600, "日桶 TTL 应大于 1 天");
        assertTrue(week(LocalDateTime.now()).ttlSeconds() > 7L * 24 * 3600, "周桶 TTL 应大于 7 天");
        assertTrue(month(LocalDateTime.now()).ttlSeconds() > 31L * 24 * 3600, "月桶 TTL 应大于 31 天");
    }

    @Test
    @DisplayName("不重置的桶不设 TTL：它就该一直留着")
    void noTtlForAll() {
        assertEquals(DrawPeriodResolver.TTL_NONE,
                DrawPeriodResolver.resolve(DrawPeriodResolver.PERIOD_ACTIVITY, LocalDateTime.now()).ttlSeconds());
    }

    // ==================== 是否需要时钟 ====================

    @Test
    @DisplayName("needsClock：只有按天/周/月才需要问数据库要时间，其余不该给热路径加往返")
    void needsClock() {
        assertTrue(DrawPeriodResolver.needsClock(DrawPeriodResolver.PERIOD_DAY));
        assertTrue(DrawPeriodResolver.needsClock(DrawPeriodResolver.PERIOD_WEEK));
        assertTrue(DrawPeriodResolver.needsClock(DrawPeriodResolver.PERIOD_MONTH));

        assertFalse(DrawPeriodResolver.needsClock(DrawPeriodResolver.PERIOD_ACTIVITY));
        assertFalse(DrawPeriodResolver.needsClock(null));
        assertFalse(DrawPeriodResolver.needsClock("unknown"));
    }

    @Test
    @DisplayName("needsClock 与 resolve 的判断口径一致：说不需要时钟的，传 null 时间也必须算得出桶")
    void needsClockConsistentWithResolve() {
        for (String period : new String[]{null, "", "ACTIVITY", "unknown", "YEAR"}) {
            assertFalse(DrawPeriodResolver.needsClock(period), "[" + period + "] 不该需要时钟");
            // 不需要时钟 => 即便不传时间也要能解析，否则热路径的短路分支会 NPE
            Period p = DrawPeriodResolver.resolve(period, null);
            assertEquals(DrawPeriodResolver.BUCKET_ALL, p.bucket());
        }
    }

    // ==================== 桶名不互相混淆 ====================

    @Test
    @DisplayName("三种周期的桶名格式互不冲突：同一时刻算出来的三个桶必须两两不同")
    void bucketsDoNotCollide() {
        LocalDateTime t = LocalDateTime.of(2026, 8, 16, 12, 0);
        String d = day(t).bucket();
        String w = week(t).bucket();
        String m = month(t).bucket();
        assertNotEquals(d, w);
        assertNotEquals(d, m);
        assertNotEquals(w, m);
        assertNotEquals(DrawPeriodResolver.BUCKET_ALL, d);
    }
}
