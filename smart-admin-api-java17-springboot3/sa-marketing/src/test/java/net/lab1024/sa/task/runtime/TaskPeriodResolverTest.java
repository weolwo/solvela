package net.lab1024.sa.task.runtime;

import net.lab1024.sa.task.constant.TaskConst;
import net.lab1024.sa.task.constant.TaskTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 周期键与幂等键解析 单元测试。
 *
 * <p>这两个键各自都不难，难的是它们<b>看起来能统一但其实不是一回事</b> ——
 * 把 period_key 当幂等键兜底会让 STREAK 直接失效（详见 §4.6 与
 * {@link TaskPeriodResolver#resolveEventBizId} 的注释）。本测试就是钉住这个区别的。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
class TaskPeriodResolverTest {

    private static final LocalDateTime APR_2 = LocalDateTime.of(2026, 4, 2, 10, 30);

    // ==================== period_key ====================

    @Test
    @DisplayName("DAILY 按自然日分片，WEEKLY 按 ISO 周分片")
    void periodKeyByLimitType() {
        assertEquals("20260402", TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, "DAILY", APR_2));
        assertEquals("2026W14", TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, "WEEKLY", APR_2));
    }

    @Test
    @DisplayName("ONCE / UNLIMITED / 未配置 都不分片")
    void periodKeyNoneCases() {
        assertEquals(TaskConst.PERIOD_NONE, TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, "ONCE", APR_2));
        assertEquals(TaskConst.PERIOD_NONE, TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, "UNLIMITED", APR_2));
        assertEquals(TaskConst.PERIOD_NONE, TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, null, APR_2));
    }

    @Test
    @DisplayName("🔴 STREAK 恒不分片，即便运营把它配成了 DAILY —— 否则连续数没有地方累加")
    void streakNeverShardsByPeriod() {
        assertEquals(TaskConst.PERIOD_NONE,
                TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.STREAK, "DAILY", APR_2),
                "按天分片会让每天都是一条新记录、current_metric 永远是 1");
        assertEquals(TaskConst.PERIOD_NONE,
                TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.STREAK, "WEEKLY", APR_2));

        // 前提确认：同样是 DAILY，COUNT 确实会分片 —— 否则上面两条是空过
        assertNotEquals(TaskConst.PERIOD_NONE,
                TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, "DAILY", APR_2));
    }

    @Test
    @DisplayName("ISO 周带上周所属年份：跨年那一周不会与自然年撞 key")
    void weeklyKeyCarriesWeekBasedYear() {
        // 2024-12-30(周一) 属于 ISO 的 2025 年第 1 周 —— 12 月的日期落在次年，
        // 若只取 getYear() 会得到 "2024W01"，与 2024 年真正的第 1 周撞成同一个 period_key
        String crossYear = TaskPeriodResolver.resolvePeriodKey(
                TaskTypeEnum.COUNT, "WEEKLY", LocalDateTime.of(2024, 12, 30, 10, 0));
        assertEquals("2025W01", crossYear);

        // 紧邻的下一周，证明周序确实在递增而不是恒为 W01
        assertEquals("2025W02", TaskPeriodResolver.resolvePeriodKey(
                TaskTypeEnum.COUNT, "WEEKLY", LocalDateTime.of(2025, 1, 6, 10, 0)));

        // 2024 年自身的第 1 周（2024-01-01 是周一），与上面的 crossYear 必须是不同的 key
        String realFirstWeekOf2024 = TaskPeriodResolver.resolvePeriodKey(
                TaskTypeEnum.COUNT, "WEEKLY", LocalDateTime.of(2024, 1, 1, 10, 0));
        assertEquals("2024W01", realFirstWeekOf2024);
        assertNotEquals(crossYear, realFirstWeekOf2024);

        // 有 53 周的年份（2026-01-01 是周四）不会溢出成次年
        assertEquals("2026W53", TaskPeriodResolver.resolvePeriodKey(
                TaskTypeEnum.COUNT, "WEEKLY", LocalDateTime.of(2026, 12, 31, 10, 0)));
    }

    // ==================== event_biz_id ====================

    @Test
    @DisplayName("上游带了单号就用单号（首尾空白要 trim）")
    void eventBizIdPrefersUpstream() {
        assertEquals("ORDER-1001", TaskPeriodResolver.resolveEventBizId("ORDER-1001", APR_2));
        assertEquals("ORDER-1001", TaskPeriodResolver.resolveEventBizId("  ORDER-1001  ", APR_2));
    }

    @Test
    @DisplayName("🔴 无单号时按事件的自然日兜底，而不是按 period_key")
    void eventBizIdFallsBackToEventDayNotPeriodKey() {
        String fallback = TaskPeriodResolver.resolveEventBizId(null, APR_2);
        assertEquals("D20260402", fallback);
        assertEquals(fallback, TaskPeriodResolver.resolveEventBizId("", APR_2));

        // 决定性的一条：STREAK 的 period_key 恒为 NONE，
        // 若拿它当幂等键，同一个人对同一任务一辈子只有一个键 —— 第一次签到后全被唯一索引挡掉
        String streakPeriodKey = TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.STREAK, "DAILY", APR_2);
        assertEquals(TaskConst.PERIOD_NONE, streakPeriodKey, "前提确认");
        assertNotEquals(streakPeriodKey, fallback, "拿 period_key 当幂等键会让「连续签到」彻底失效");
    }

    @Test
    @DisplayName("兜底幂等键逐日不同：连续签到才推得动")
    void fallbackEventBizIdDiffersPerDay() {
        String day1 = TaskPeriodResolver.resolveEventBizId(null, LocalDateTime.of(2026, 4, 1, 23, 59));
        String day2 = TaskPeriodResolver.resolveEventBizId(null, LocalDateTime.of(2026, 4, 2, 0, 1));
        assertNotEquals(day1, day2);
    }

    @Test
    @DisplayName("同一天内不同时刻的兜底键相同：一天只算一次")
    void fallbackEventBizIdSameWithinDay() {
        assertEquals(
                TaskPeriodResolver.resolveEventBizId(null, LocalDateTime.of(2026, 4, 2, 0, 0)),
                TaskPeriodResolver.resolveEventBizId(null, LocalDateTime.of(2026, 4, 2, 23, 59)));
    }
}
