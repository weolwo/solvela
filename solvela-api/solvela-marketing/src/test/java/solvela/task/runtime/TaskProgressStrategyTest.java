package solvela.task.runtime;

import solvela.task.constant.TaskConst;
import solvela.task.constant.TaskDiscardCode;
import solvela.task.constant.TaskTypeEnum;
import solvela.task.record.domain.entity.TaskRecord;
import solvela.task.runtime.domain.MetricPlan;
import solvela.task.runtime.domain.TaskEventContext;
import solvela.task.runtime.domain.TaskProgressData;
import solvela.task.runtime.domain.TaskRuleConfig;
import solvela.task.runtime.strategy.AmountTaskStrategy;
import solvela.task.runtime.strategy.CountTaskStrategy;
import solvela.task.runtime.strategy.SimpleTaskStrategy;
import solvela.task.runtime.strategy.StreakTaskStrategy;
import solvela.task.runtime.strategy.TaskProgressStrategy;
import solvela.task.runtime.strategy.TaskProgressStrategyFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务进度策略 单元测试（纯内存，无 Spring 上下文、无数据库）。
 *
 * <p>策略被设计成纯函数正是为了能这样测：不查库、不写库、不依赖当前时间。
 * 覆盖重点是几个「不测就一定会错」的点：
 * STREAK 断档的 off-by-one、AMOUNT 的门槛与丢弃原因、达标容差、以及幂等键必须带档位。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
class TaskProgressStrategyTest {

    private final SimpleTaskStrategy simple = new SimpleTaskStrategy();
    private final CountTaskStrategy count = new CountTaskStrategy();
    private final AmountTaskStrategy amount = new AmountTaskStrategy();
    private final StreakTaskStrategy streak = new StreakTaskStrategy();

    private static final LocalDateTime DAY_1 = LocalDateTime.of(2026, 4, 1, 10, 0);
    private static final LocalDateTime DAY_2 = LocalDateTime.of(2026, 4, 2, 10, 0);
    private static final LocalDateTime DAY_3 = LocalDateTime.of(2026, 4, 3, 10, 0);
    private static final LocalDateTime DAY_5 = LocalDateTime.of(2026, 4, 5, 10, 0);

    private TaskRecord record(String metric, String progressData) {
        TaskRecord record = new TaskRecord();
        record.setId(1024L);
        record.setMemberId(5579345309L);
        record.setTaskConfigId(1L);
        record.setCurrentMetric(new BigDecimal(metric));
        record.setProgressData(progressData);
        record.setVersion(0);
        record.setStatus(TaskConst.RECORD_STATUS_RUNNING);
        return record;
    }

    private TaskEventContext event(LocalDateTime time, String amountValue) {
        return new TaskEventContext("DAILY_SIGN", 5579345309L, "tester", "biz-1",
                amountValue == null ? null : new BigDecimal(amountValue), time, null, Map.of());
    }

    private TaskRuleConfig rule(Map<String, Object> raw) {
        return new TaskRuleConfig(raw);
    }

    // ==================== COUNT / SIMPLE ====================

    @Test
    @DisplayName("COUNT：每次事件恒 +1，走原子累加而不是读-改-写")
    void countAlwaysAccumulatesOne() {
        MetricPlan plan = count.plan(record("5", null), event(DAY_1, null), rule(Map.of("targetCount", 7)));

        MetricPlan.Accumulate accumulate = assertInstanceOf(MetricPlan.Accumulate.class, plan,
                "COUNT 必须返回 Accumulate —— 用 Overwrite 就等于主动把一个能一条 SQL 解决的问题降级成需要重试的问题");
        assertEquals(0, BigDecimal.ONE.compareTo(accumulate.delta()));
    }

    @Test
    @DisplayName("SIMPLE：模板没配 targetCount 也必须能达标（否则「点了但永远不完成」）")
    void simpleCompletesWithoutExplicitTarget() {
        TaskRuleConfig empty = rule(Map.of());
        assertNull(empty.target(), "前提确认：这条规则确实没配目标值，否则本用例是空过");

        assertTrue(simple.isCompleted(BigDecimal.ONE, empty));
        assertFalse(simple.isCompleted(BigDecimal.ZERO, empty));
    }

    // ==================== AMOUNT ====================

    @Test
    @DisplayName("AMOUNT：按事件金额累加")
    void amountAccumulatesEventAmount() {
        MetricPlan plan = amount.plan(record("0", null), event(DAY_1, "99.50"),
                rule(Map.of("targetAmount", 500)));

        MetricPlan.Accumulate accumulate = assertInstanceOf(MetricPlan.Accumulate.class, plan);
        assertEquals(0, new BigDecimal("99.50").compareTo(accumulate.delta()));
    }

    @Test
    @DisplayName("AMOUNT：未达单笔门槛要丢弃，且原因必须是人话（客诉自证的答案就在这里）")
    void amountBelowThresholdIsDiscardedWithReadableReason() {
        MetricPlan plan = amount.plan(record("0", null), event(DAY_1, "99"),
                rule(Map.of("targetAmount", 500, "minAmount", 100)));

        MetricPlan.Skip skip = assertInstanceOf(MetricPlan.Skip.class, plan);
        assertTrue(skip.reason().contains("99"), "原因里要有实际金额：" + skip.reason());
        assertTrue(skip.reason().contains("100"), "原因里要有门槛值：" + skip.reason());
        // 文本给人读、码给机器读，两个都得有 —— 只有码统计不出客诉，只有文本聚不了类
        assertEquals(TaskDiscardCode.AMOUNT_BELOW_MIN, skip.code());
    }

    @Test
    @DisplayName("AMOUNT：零金额事件丢弃，不静默当 0 累加")
    void amountZeroIsDiscarded() {
        MetricPlan.Skip skip = assertInstanceOf(MetricPlan.Skip.class,
                amount.plan(record("0", null), event(DAY_1, "0"), rule(Map.of("targetAmount", 500))));
        assertEquals(TaskDiscardCode.AMOUNT_MISSING, skip.code());
    }

    @Test
    @DisplayName("丢弃分类：三类「需要人介入」与「正常业务规则」必须分得开")
    void discardCodeSeparatesActionableFromNormal() {
        // 正常业务规则导致的丢弃，量再大也不用管
        assertFalse(TaskDiscardCode.AMOUNT_BELOW_MIN.needsAttention());
        assertFalse(TaskDiscardCode.AUDIENCE_MISMATCH.needsAttention());
        assertFalse(TaskDiscardCode.ROUND_LIMIT_EXCEEDED.needsAttention());
        assertFalse(TaskDiscardCode.RECORD_NOT_RUNNING.needsAttention());
        assertFalse(TaskDiscardCode.STREAK_SAME_DAY.needsAttention());

        // 这三类哪怕只有几条都该报警：分别是上游漏传、配置坏了、系统过载
        assertTrue(TaskDiscardCode.AUDIENCE_UNKNOWN.needsAttention());
        assertTrue(TaskDiscardCode.CONFIG_INVALID.needsAttention());
        assertTrue(TaskDiscardCode.POOL_REJECTED.needsAttention());
    }

    @Test
    @DisplayName("丢弃分类：resolve 只认精确值，且每个码都有人话描述（大屏直接拿来当分类名）")
    void discardCodeResolveAndDesc() {
        for (TaskDiscardCode code : TaskDiscardCode.values()) {
            assertEquals(code, TaskDiscardCode.resolve(code.getValue()));
            assertNotNull(code.getDesc());
            assertFalse(code.getDesc().isBlank(), code.name() + " 缺少描述");
        }
        assertNull(TaskDiscardCode.resolve("NOT_A_CODE"));
    }

    // ==================== STREAK ====================

    @Test
    @DisplayName("STREAK：首次命中 = 1，并记下 lastHitDate")
    void streakFirstHit() {
        MetricPlan plan = streak.plan(record("0", null), event(DAY_1, null),
                rule(Map.of("targetCount", 7)));

        MetricPlan.Overwrite overwrite = assertInstanceOf(MetricPlan.Overwrite.class, plan,
                "STREAK 是唯一走读-改-写的类型");
        assertEquals(0, BigDecimal.ONE.compareTo(overwrite.metric()));
        assertEquals("20260401", TaskProgressData.parse(overwrite.progressData()).lastHitDate());
    }

    @Test
    @DisplayName("STREAK：连着第二天 +1")
    void streakConsecutiveDayIncrements() {
        TaskRecord record = record("3", "{\"lastHitDate\":\"20260401\"}");
        MetricPlan plan = streak.plan(record, event(DAY_2, null), rule(Map.of("targetCount", 7)));

        MetricPlan.Overwrite overwrite = assertInstanceOf(MetricPlan.Overwrite.class, plan);
        assertEquals(0, new BigDecimal("4").compareTo(overwrite.metric()));
    }

    @Test
    @DisplayName("🔴 STREAK 断档是「归零再+1」而不是「归零」—— 断档当天本身也是有效的一次")
    void streakBreakResetsToOneNotZero() {
        // 上次 4/1，本次 4/5，中间断了 3 天，tolerance=0
        TaskRecord record = record("3", "{\"lastHitDate\":\"20260401\"}");
        MetricPlan plan = streak.plan(record, event(DAY_5, null), rule(Map.of("targetCount", 7)));

        MetricPlan.Overwrite overwrite = assertInstanceOf(MetricPlan.Overwrite.class, plan);
        assertEquals(0, BigDecimal.ONE.compareTo(overwrite.metric()),
                "断档后必须是 1 不是 0 —— 这是连续型任务最经典的 off-by-one");
        assertNotEquals(0, BigDecimal.ZERO.compareTo(overwrite.metric()));
    }

    @Test
    @DisplayName("STREAK：tolerance=1 时允许断一天（间隔 2 天仍算连上）")
    void streakToleranceAllowsOneMiss() {
        TaskRecord record = record("3", "{\"lastHitDate\":\"20260401\"}");

        // 间隔 2 天 = 断了 1 天，在 tolerance=1 的容忍范围内
        MetricPlan within = streak.plan(record, event(DAY_3, null),
                rule(Map.of("targetCount", 7, "tolerance", 1)));
        assertEquals(0, new BigDecimal("4").compareTo(
                assertInstanceOf(MetricPlan.Overwrite.class, within).metric()));

        // 同样的间隔，tolerance=0 时就该清零重来 —— 证明上一条不是恰好通过
        MetricPlan beyond = streak.plan(record, event(DAY_3, null),
                rule(Map.of("targetCount", 7, "tolerance", 0)));
        assertEquals(0, BigDecimal.ONE.compareTo(
                assertInstanceOf(MetricPlan.Overwrite.class, beyond).metric()));
    }

    @Test
    @DisplayName("STREAK：同一天重复事件不累加（幂等被绕过时的防御分支）")
    void streakSameDayIsSkipped() {
        TaskRecord record = record("3", "{\"lastHitDate\":\"20260401\"}");
        MetricPlan plan = streak.plan(record, event(DAY_1, null), rule(Map.of("targetCount", 7)));

        assertEquals(TaskDiscardCode.STREAK_SAME_DAY,
                assertInstanceOf(MetricPlan.Skip.class, plan).code());
    }

    @Test
    @DisplayName("STREAK：lastHitDate 是脏数据时按断档处理，绝不凭空送一个连续数")
    void streakDirtyLastHitDateFallsBackToOne() {
        TaskRecord record = record("6", "{\"lastHitDate\":\"not-a-date\"}");
        MetricPlan plan = streak.plan(record, event(DAY_2, null), rule(Map.of("targetCount", 7)));

        assertEquals(0, BigDecimal.ONE.compareTo(
                assertInstanceOf(MetricPlan.Overwrite.class, plan).metric()));
    }

    @Test
    @DisplayName("STREAK：progress_data 里不认识的键必须原样带回，不能被抹掉")
    void streakPreservesUnknownProgressKeys() {
        TaskRecord record = record("1", "{\"lastHitDate\":\"20260401\",\"customKey\":\"keep-me\"}");
        MetricPlan plan = streak.plan(record, event(DAY_2, null), rule(Map.of("targetCount", 7)));

        String progressJson = assertInstanceOf(MetricPlan.Overwrite.class, plan).progressData();
        assertTrue(progressJson.contains("keep-me"),
                "后加的键被上一版本代码悄悄抹掉，这类丢失几乎不可能在测试里被发现：" + progressJson);
    }

    // ==================== 达标判定 ====================

    @Test
    @DisplayName("达标判定带容差：499.99999999999994 必须算作满 500（铁律 1）")
    void completionUsesTolerance() {
        TaskRuleConfig config = rule(Map.of("targetAmount", 500));

        assertTrue(amount.isCompleted(new BigDecimal("499.99999999999994"), config),
                "浮点求和判等不带容差就会出现「界面显示已满 500 却判不达标」");
        assertTrue(amount.isCompleted(new BigDecimal("500"), config));
        assertFalse(amount.isCompleted(new BigDecimal("499.9"), config));
    }

    @Test
    @DisplayName("目标值未配置时不算达标（否则每个事件都会立刻达标发奖）")
    void missingTargetIsNotCompleted() {
        assertFalse(count.isCompleted(new BigDecimal("100"), rule(Map.of())));
    }

    @Test
    @DisplayName("兼容存量模板的 targetDays 键：主形态优先，缺失才兜底")
    void targetFallsBackToLegacyKey() {
        // 存量模板 FRWAYF2X6N（每日签到）的 ui_schema 用的就是 targetDays。
        // 不兜这个键的话，线上那条任务会「进度照涨、永远不完成」，且一条报错都没有
        assertEquals(0, new BigDecimal("7").compareTo(rule(Map.of("targetDays", 7)).target()));
        assertTrue(count.isCompleted(new BigDecimal("7"), rule(Map.of("targetDays", 7))));

        // 主形态优先级更高：两个键并存时以 targetCount 为准
        assertEquals(0, new BigDecimal("3").compareTo(
                rule(Map.of("targetCount", 3, "targetDays", 7)).target()));
        // AMOUNT 的 targetAmount 优先级最高
        assertEquals(0, new BigDecimal("500").compareTo(
                rule(Map.of("targetAmount", 500, "targetCount", 3)).target()));
    }

    // ==================== 策略工厂（铁律 14） ====================

    @Test
    @DisplayName("策略工厂：四种类型都能解析到实现，且不接受用字符串查")
    void factoryResolvesAllTypes() {
        TaskProgressStrategyFactory factory = new TaskProgressStrategyFactory(
                List.of(simple, count, amount, streak));

        for (TaskTypeEnum type : TaskTypeEnum.values()) {
            TaskProgressStrategy resolved = factory.resolve(type);
            assertNotNull(resolved, "没有支持 " + type.getValue() + " 的策略实现");
            assertEquals(type, resolved.supportType());
        }
        assertNull(factory.resolve(null));
    }

    @Test
    @DisplayName("策略工厂：重复注册要在启动期就炸，而不是让后注册的那个静默收不到事件")
    void factoryRejectsDuplicateSupportType() {
        assertThrows(IllegalStateException.class,
                () -> new TaskProgressStrategyFactory(List.of(count, new CountTaskStrategy())));
    }

    // ==================== 幂等键（方案 §4.3） ====================

    @Test
    @DisplayName("🔴 发奖幂等键必须带档位：不带的话阶梯任务第二档会被唯一索引静默吞掉")
    void sourceBizIdMustCarryStageLevel() {
        String stage1 = TaskConst.buildSourceBizId(1024L, 1);
        String stage2 = TaskConst.buildSourceBizId(1024L, 2);

        assertNotEquals(stage1, stage2,
                "两档的 external_biz_no 相同 -> t_prize_log.uk_external_biz 撞索引 -> "
                        + "PrizeDispatchHandler 的 catch(DuplicateKeyException) 把第二档当重复派发丢弃");
        assertEquals("1024:1", stage1);
        assertEquals("1024:2", stage2);
        // 同一档重复投递仍要撞上，那才是防重该起作用的地方
        assertEquals(stage2, TaskConst.buildSourceBizId(1024L, 2));
    }
}
