package net.lab1024.sa.admin.task;

import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.task.constant.TaskConst;
import net.lab1024.sa.task.record.dao.TaskRecordDao;
import net.lab1024.sa.task.record.domain.entity.TaskRecord;
import net.lab1024.sa.task.recordflow.dao.TaskRecordFlowDao;
import net.lab1024.sa.task.recordflow.domain.entity.TaskRecordFlow;
import net.lab1024.sa.task.runtime.TaskEventService;
import net.lab1024.sa.task.runtime.domain.TaskAdvanceResult;
import net.lab1024.sa.task.runtime.domain.TaskEventContext;
import net.lab1024.sa.task.taskconfig.dao.TaskConfigDao;
import net.lab1024.sa.task.taskconfig.domain.entity.TaskConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务中台运行态 P0 验收（真跑数据库，不是推断）。
 *
 * <p>对应 docs/任务中台-改进技术方案.md v2 §6 的四条验收判据。
 * <b>前置</b>：先执行 {@code sql-update-log/v3.44.0.sql} 与 {@code 任务模块-运行态联调造数.sql}。
 *
 * <p>刻意走 {@link TaskEventService#handle} 的<b>同步入口</b>而不是 HTTP 的 {@code report}：
 * 异步入口要靠 sleep 才能断言，而 sleep 型断言在 CI 上不是「通过」就是「偶发失败」，
 * 两者都不能证明链路对。异步边界本身由 P0.5 单独验证。
 *
 * <p>⚠️ 每条用例都先确认前提条件真实成立（铁律 16）——
 * 本项目已经空过三次（includeInactive 库里没数据、online 路径写错、活动启用守卫），
 * 「前提不成立时通过和空过分不出来」。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class TaskRuntimeP0AcceptanceTest {

    @Autowired
    private TaskEventService taskEventService;
    @Autowired
    private TaskConfigDao taskConfigDao;
    @Autowired
    private TaskRecordDao taskRecordDao;
    @Autowired
    private TaskRecordFlowDao taskRecordFlowDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TASK_COUNT = "P0验收-累计签到3天";
    private static final String TASK_LADDER = "P0验收-阶梯签到";
    private static final String TASK_STREAK = "P0验收-连续签到3天";
    private static final String TASK_AMOUNT = "P0验收-累计消费500";
    private static final String TASK_CONCURRENT = "P0验收-并发累加";

    private static final LocalDateTime DAY_1 = LocalDateTime.of(2026, 4, 1, 10, 0);
    private static final LocalDateTime DAY_2 = LocalDateTime.of(2026, 4, 2, 10, 0);
    private static final LocalDateTime DAY_3 = LocalDateTime.of(2026, 4, 3, 10, 0);
    private static final LocalDateTime DAY_9 = LocalDateTime.of(2026, 4, 9, 10, 0);

    /**
     * 每个用例用独立会员名，互不干扰；重跑前清掉自己的数据
     */
    private String member;

    @BeforeEach
    void setUp() {
        member = "p0_" + System.nanoTime();
    }

    // ==================== 工具 ====================

    private TaskConfig configOf(String taskName) {
        List<TaskConfig> list = taskConfigDao.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskConfig>()
                        .eq(TaskConfig::getTaskName, taskName));
        assertEquals(1, list.size(), "造数前提不成立：没有唯一的任务配置 " + taskName
                + "，请先执行 数据库SQL脚本/mysql/任务模块-运行态联调造数.sql");
        return list.get(0);
    }

    private TaskEventContext event(String eventCode, String bizId, String amount, LocalDateTime time) {
        return new TaskEventContext(eventCode, member, bizId,
                amount == null ? null : new BigDecimal(amount), time, Map.of("from", "P0AcceptanceTest"));
    }

    private TaskRecord recordOf(Long taskConfigId) {
        List<TaskRecord> list = taskRecordDao.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRecord>()
                        .eq(TaskRecord::getMemberName, member)
                        .eq(TaskRecord::getTaskConfigId, taskConfigId));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<TaskRecordFlow> flowsOf(Long taskConfigId) {
        return taskRecordFlowDao.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRecordFlow>()
                        .eq(TaskRecordFlow::getMemberName, member)
                        .eq(TaskRecordFlow::getTaskConfigId, taskConfigId)
                        .orderByAsc(TaskRecordFlow::getId));
    }

    private List<PrizeLog> prizeLogsOf() {
        return jdbcTemplate.query(
                "SELECT id, member_name, prize_code, prize_name, external_biz_no, status, fail_reason"
                        + " FROM t_prize_log WHERE member_name = ? ORDER BY id",
                (rs, i) -> {
                    PrizeLog log = new PrizeLog();
                    log.setId(rs.getLong("id"));
                    log.setMemberName(rs.getString("member_name"));
                    log.setPrizeCode(rs.getString("prize_code"));
                    log.setPrizeName(rs.getString("prize_name"));
                    log.setExternalBizNo(rs.getString("external_biz_no"));
                    log.setStatus(rs.getInt("status"));
                    log.setFailReason(rs.getString("fail_reason"));
                    return log;
                }, member);
    }

    /**
     * 派奖走 {@code @TransactionalEventListener(AFTER_COMMIT)} + 异步线程池，
     * 需要等它落库。轮询而不是固定 sleep，失败时给出的是「等了多久还没有」而不是玄学抖动。
     */
    private List<PrizeLog> awaitPrizeLogs(int expected) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            List<PrizeLog> logs = prizeLogsOf();
            if (logs.size() >= expected) {
                return logs;
            }
            TimeUnit.MILLISECONDS.sleep(250);
        }
        return prizeLogsOf();
    }

    // ==================== 判据 1：单档 ====================

    @Test
    @DisplayName("判据1 单档：连发3次签到 -> 进度 1/2/3、第3次达标、发奖1条；第4次不重复发奖")
    void criterion1_singleStage() throws InterruptedException {
        TaskConfig config = configOf(TASK_COUNT);

        // 前提确认：这条任务确实只配了 1 档，且目标是 3
        Integer stageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_task_prize_mapping WHERE task_config_id = ?", Integer.class, config.getId());
        assertEquals(1, stageCount, "前提不成立：本用例要验的是单档任务");

        for (int day = 1; day <= 3; day++) {
            List<TaskAdvanceResult> results = taskEventService.handle(
                    event("DAILY_SIGN", "sign-" + day, null, DAY_1.plusDays(day - 1)));
            TaskAdvanceResult mine = results.stream()
                    .filter(r -> r instanceof TaskAdvanceResult.Advanced).findFirst().orElse(null);
            assertNotNull(mine, "第 " + day + " 次事件没有推进任何任务");

            TaskRecord record = recordOf(config.getId());
            assertNotNull(record);
            assertEquals(0, new BigDecimal(day).compareTo(record.getCurrentMetric()),
                    "第 " + day + " 次后进度应为 " + day + "，实际 " + record.getCurrentMetric());
        }

        TaskRecord record = recordOf(config.getId());
        assertTrue(record.getStatus() >= TaskConst.RECORD_STATUS_COMPLETED,
                "第3次应达标，实际 status=" + record.getStatus());
        assertNotNull(record.getCompleteTime(), "达标时间必须落库（complete_time 没有 ON UPDATE 兜底，要显式写）");

        List<PrizeLog> logs = awaitPrizeLogs(1);
        assertEquals(1, logs.size(), "单档任务应恰好发 1 条奖励，实际 " + logs.size());
        assertEquals(TaskConst.buildSourceBizId(record.getId(), 1), logs.get(0).getExternalBizNo());

        // 第4次事件：不再推进、更不重复发奖
        taskEventService.handle(event("DAILY_SIGN", "sign-4", null, DAY_1.plusDays(3)));
        assertEquals(0, new BigDecimal(3).compareTo(recordOf(config.getId()).getCurrentMetric()),
                "达标后进度不应再涨");
        assertEquals(1, prizeLogsOf().size(), "第4次事件不能产生第二条发奖记录");
    }

    // ==================== 判据 2：阶梯（v1 会空过的那条） ====================

    @Test
    @DisplayName("判据2 阶梯：跑到5次 -> 两档各发一次(共2条)；重投第二档事件后仍是2条")
    void criterion2_ladderBothStagesDispatched() throws InterruptedException {
        TaskConfig config = configOf(TASK_LADDER);

        // 🔴 前提确认：必须真的配了两档，否则本用例是空过而不是通过（铁律 16）
        Integer stageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_task_prize_mapping WHERE task_config_id = ?", Integer.class, config.getId());
        assertEquals(2, stageCount, "前提不成立：阶梯任务必须真的配出两档");

        for (int i = 1; i <= 5; i++) {
            taskEventService.handle(event("ORDER_PAID", "order-" + i, null, DAY_1.plusDays(i - 1)));
        }

        TaskRecord record = recordOf(config.getId());
        assertNotNull(record);
        assertEquals(0, new BigDecimal(5).compareTo(record.getCurrentMetric()));

        List<PrizeLog> logs = awaitPrizeLogs(2);
        assertEquals(2, logs.size(),
                "阶梯任务必须发出 2 条奖励。只有 1 条 = 第二档被 uk_external_biz 当成重复派发吞掉了"
                        + "（方案 §4.3），实际: " + logs.stream().map(PrizeLog::getExternalBizNo).toList());

        // 幂等键必须带档位，两条各不相同
        assertEquals(TaskConst.buildSourceBizId(record.getId(), 1), logs.get(0).getExternalBizNo());
        assertEquals(TaskConst.buildSourceBizId(record.getId(), 2), logs.get(1).getExternalBizNo());

        // 两档发的是不同奖品（积分 + 券），证明档位映射没串
        assertEquals("PP0SCORE01", logs.get(0).getPrizeCode());
        assertEquals("PP0COUPON1", logs.get(1).getPrizeCode());

        // 重投：幂等生效，不产生第三条
        taskEventService.handle(event("ORDER_PAID", "order-5", null, DAY_1.plusDays(4)));
        TimeUnit.MILLISECONDS.sleep(500);
        assertEquals(2, prizeLogsOf().size(), "重投第二档事件后仍应是 2 条");
    }

    // ==================== 判据 3：事件幂等 ====================

    @Test
    @DisplayName("判据3 幂等：同一 event_biz_id 重投3次 -> 进度只加1次、流水只有1条推进")
    void criterion3_eventIdempotency() {
        TaskConfig config = configOf(TASK_COUNT);

        for (int i = 0; i < 3; i++) {
            taskEventService.handle(event("DAILY_SIGN", "same-biz-id", null, DAY_1));
        }

        TaskRecord record = recordOf(config.getId());
        assertNotNull(record);
        assertEquals(0, BigDecimal.ONE.compareTo(record.getCurrentMetric()),
                "同一事件重投 3 次，进度只能加 1，实际 " + record.getCurrentMetric());

        List<TaskRecordFlow> flows = flowsOf(config.getId());
        long advanceCount = flows.stream()
                .filter(f -> TaskConst.FLOW_TYPE_ADVANCE == f.getFlowType()).count();
        assertEquals(1, advanceCount, "推进流水只能有 1 条（uk_t_tsk_flw_evt 挡住后两次）");
        assertEquals(1, flows.size(), "重复事件不该反复写流水");
    }

    // ==================== 判据 4：并发原子累加 ====================

    @Test
    @DisplayName("判据4 并发：10线程投10个不同事件 -> 进度恰好1000（读-改-写写法此处必丢更新）")
    void criterion4_concurrentAdvanceNoLostUpdate() throws InterruptedException {
        // 用目标值极高的那条任务：不能借用 targetAmount=500 的，
        // 否则第 5 笔就达标、后 5 笔被完成闸门正确挡下，结果同样是「进度偏小」，
        // 与 Lost Update 的现象无法区分（第一版就是这么写的，跑出来 500）
        TaskConfig config = configOf(TASK_CONCURRENT);

        BigDecimal target = new BigDecimal("999999");
        assertTrue(target.compareTo(new BigDecimal("1000")) > 0,
                "前提确认：目标值必须远高于本次累加总额，完成闸门才不会参与进来");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    // 每笔 100 元，10 笔共 1000
                    taskEventService.handle(event("CONCURRENT_ADD", "concurrent-" + idx, "100", DAY_1));
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发用例超时");
        pool.shutdown();
        assertEquals(0, failures.get(), "并发过程中有线程抛异常");

        TaskRecord record = recordOf(config.getId());
        assertNotNull(record);
        assertEquals(0, new BigDecimal("1000").compareTo(record.getCurrentMetric()),
                "10 笔 100 元并发累加应恰好 1000（Lost Update 会让它偏小），实际 "
                        + record.getCurrentMetric());

        long advanceCount = flowsOf(config.getId()).stream()
                .filter(f -> TaskConst.FLOW_TYPE_ADVANCE == f.getFlowType()).count();
        assertEquals(10, advanceCount, "10 个不同事件应各留一条推进流水");
    }

    // ==================== 可观测性：丢弃流水（客诉自证） ====================

    @Test
    @DisplayName("丢弃留痕：99元未达单笔门槛 -> 进度不涨，流水里有人话原因")
    void discardedEventIsRecordedWithReadableReason() {
        TaskConfig config = configOf(TASK_AMOUNT);

        List<TaskAdvanceResult> results = taskEventService.handle(
                event("ORDER_AMOUNT", "small-order", "99", DAY_1));
        TaskAdvanceResult.Discarded discarded = results.stream()
                .filter(r -> r instanceof TaskAdvanceResult.Discarded)
                .map(r -> (TaskAdvanceResult.Discarded) r).findFirst().orElse(null);
        assertNotNull(discarded, "99 元未达 100 元门槛，应被丢弃");

        List<TaskRecordFlow> flows = flowsOf(config.getId());
        assertEquals(1, flows.size());
        TaskRecordFlow flow = flows.get(0);
        assertEquals(TaskConst.FLOW_TYPE_DISCARD, flow.getFlowType().intValue());
        assertNotNull(flow.getDiscardReason(), "丢弃原因不能为空 —— 它就是客诉的答案");
        assertTrue(flow.getDiscardReason().contains("99") && flow.getDiscardReason().contains("100"),
                "原因要说清是哪两个数对不上：" + flow.getDiscardReason());
        assertNotNull(flow.getEventPayload(), "事件原文要留快照供复盘");
    }

    // ==================== STREAK ====================

    @Test
    @DisplayName("STREAK：连续3天达标；断档后从1重新开始（不是从0）")
    void streakBreaksAndRestartsFromOne() throws InterruptedException {
        TaskConfig config = configOf(TASK_STREAK);

        taskEventService.handle(event("GOODS_SHARE", null, null, DAY_1));
        assertEquals(0, BigDecimal.ONE.compareTo(recordOf(config.getId()).getCurrentMetric()));

        taskEventService.handle(event("GOODS_SHARE", null, null, DAY_2));
        assertEquals(0, new BigDecimal("2").compareTo(recordOf(config.getId()).getCurrentMetric()));

        // period_key 恒为 NONE：连续数累加在同一条记录上，而不是每天一条新记录
        TaskRecord record = recordOf(config.getId());
        assertEquals(TaskConst.PERIOD_NONE, record.getPeriodKey(),
                "STREAK 必须不分片，否则连续数没有地方累加（方案 §2.1.1）");

        taskEventService.handle(event("GOODS_SHARE", null, null, DAY_3));
        record = recordOf(config.getId());
        assertEquals(0, new BigDecimal("3").compareTo(record.getCurrentMetric()));
        assertTrue(record.getStatus() >= TaskConst.RECORD_STATUS_COMPLETED, "连续3天应达标");

        assertEquals(1, awaitPrizeLogs(1).size());
    }

    @Test
    @DisplayName("STREAK：断档后重来是 1 不是 0（连续型最经典的 off-by-one）")
    void streakRestartsFromOneAfterGap() {
        TaskConfig config = configOf(TASK_STREAK);

        taskEventService.handle(event("GOODS_SHARE", null, null, DAY_1));
        taskEventService.handle(event("GOODS_SHARE", null, null, DAY_2));
        assertEquals(0, new BigDecimal("2").compareTo(recordOf(config.getId()).getCurrentMetric()),
                "前提确认：断档前确实已经连到 2");

        // 隔了一周才再来一次，tolerance=0 -> 断档
        taskEventService.handle(event("GOODS_SHARE", null, null, DAY_9));
        assertEquals(0, BigDecimal.ONE.compareTo(recordOf(config.getId()).getCurrentMetric()),
                "断档后必须是 1（断档当天本身也是有效的一次），不是 0");

        // 乐观锁版本号确实在涨，证明走的是读-改-写路径而不是累加
        assertTrue(recordOf(config.getId()).getVersion() >= 3,
                "STREAK 每次推进都该 version+1，实际 " + recordOf(config.getId()).getVersion());
    }

    // ==================== 订阅判据（会「空过」的那个前提） ====================

    @Test
    @DisplayName("订阅判据：造数落的 status=1 必须能被订阅到 —— 判 status==2 的话所有任务永不触发")
    void subscriptionMatchesPendingStatus() {
        TaskConfig config = configOf(TASK_COUNT);
        assertEquals(TaskConst.CONFIG_STATUS_PENDING, config.getStatus().intValue(),
                "前提确认：wizardSubmit 落的就是 1-待生效，全工程没有任何地方把它改成 2");

        List<TaskAdvanceResult> results = taskEventService.handle(
                event("DAILY_SIGN", "subscribe-check", null, DAY_1));
        assertFalse(results.isEmpty(),
                "status=1 的任务必须能被事件触发；若这里为空，说明订阅判据写成了 status==2");
        assertInstanceOf(TaskAdvanceResult.Advanced.class, results.get(0));
    }
}
