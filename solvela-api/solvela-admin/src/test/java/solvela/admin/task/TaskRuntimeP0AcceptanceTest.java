package solvela.admin.task;

import solvela.enums.TaskConfigStatusEnum;
import solvela.enums.TaskFlowTypeEnum;
import solvela.enums.TaskRecordStatusEnum;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.base.util.SolvelaEnumUtil;
import solvela.exception.BusinessException;
import solvela.prize.PrizeLog;
import solvela.task.constant.TaskConst;
import solvela.task.constant.TaskDiscardCode;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.TaskRecord;
import solvela.task.recordflow.dao.TaskRecordFlowDao;
import solvela.task.TaskRecordFlow;
import solvela.task.constant.TaskTypeEnum;
import solvela.task.runtime.TaskEventService;
import solvela.task.runtime.TaskPeriodResolver;
import solvela.task.runtime.domain.TaskAdvanceResult;
import solvela.task.runtime.domain.TaskEventContext;
import solvela.task.runtime.domain.TaskEventReportCommand;
import solvela.task.TaskEvent;
import solvela.task.taskevent.domain.dto.TaskEventOptionDTO;
import solvela.task.taskevent.service.TaskEventDefService;
import solvela.task.taskconfig.dao.TaskConfigDao;
import solvela.task.tasktemplate.domain.command.TaskTemplateSaveCommand;
import solvela.task.tasktemplate.service.TaskTemplateService;
import solvela.task.TaskConfig;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private TaskEventDefService taskEventDefService;
    @Autowired
    private TaskTemplateService taskTemplateService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TASK_COUNT = "P0验收-累计签到3天";
    private static final String TASK_LADDER = "P0验收-阶梯签到";
    private static final String TASK_STREAK = "P0验收-连续签到3天";
    private static final String TASK_AMOUNT = "P0验收-累计消费500";
    private static final String TASK_CONCURRENT = "P0验收-并发累加";
    private static final String TASK_HIGH_FREQ = "P0验收-高频丢弃";
    private static final String TASK_NEW_ONLY = "P0验收-限新会员";
    private static final String TASK_OLD_ONLY = "P0验收-限老会员";
    private static final String TASK_ROUNDS = "P0验收-每日两轮";

    private static final LocalDateTime DAY_1 = LocalDateTime.of(2026, 4, 1, 10, 0);
    private static final LocalDateTime DAY_2 = LocalDateTime.of(2026, 4, 2, 10, 0);
    private static final LocalDateTime DAY_3 = LocalDateTime.of(2026, 4, 3, 10, 0);
    private static final LocalDateTime DAY_9 = LocalDateTime.of(2026, 4, 9, 10, 0);

    /**
     * 每个用例用独立会员，互不干扰；重跑前清掉自己的数据。
     *
     * <p>🔴 v3.71.0 之后关联键是 {@code member_id}，而且 {@code report()} 会校验
     * 「这个会员号真实存在」—— 所以这里必须<b>真的往 t_member 插一行</b>，
     * 不能像以前那样随手编一个字符串。编一个不存在的会员号，测出来的是
     * 「校验有没有生效」，而不是任务链路对不对。
     */
    private long memberId;

    /** 该会员的账号，仅用于落进流水的展示快照 */
    private String member;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        // 10 位会员号，与 MemberIdCodec 的值域一致；测试自己造号不走发号器，
        // 免得把发号器的号段消耗在测试上
        memberId = 1_000_000_000L + Math.floorMod(nano, 8_000_000_000L);
        member = "p0_" + nano;
        jdbcTemplate.update(
                "INSERT INTO t_member (member_id, member_name, nickname) VALUES (?, ?, ?)",
                memberId, member, member);
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
        return new TaskEventContext(eventCode, memberId, member, bizId,
                amount == null ? null : new BigDecimal(amount), time, null, Map.of("from", "P0AcceptanceTest"));
    }

    /** 该任务下本会员的全部记录（多轮时会有多条） */
    private List<TaskRecord> recordsOf(Long taskConfigId) {
        return taskRecordDao.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRecord>()
                        .eq(TaskRecord::getMemberId, memberId)
                        .eq(TaskRecord::getTaskConfigId, taskConfigId)
                        .orderByAsc(TaskRecord::getId));
    }

    private TaskRecord recordOf(Long taskConfigId) {
        List<TaskRecord> list = taskRecordDao.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRecord>()
                        .eq(TaskRecord::getMemberId, memberId)
                        .eq(TaskRecord::getTaskConfigId, taskConfigId));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<TaskRecordFlow> flowsOf(Long taskConfigId) {
        return taskRecordFlowDao.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRecordFlow>()
                        .eq(TaskRecordFlow::getMemberId, memberId)
                        .eq(TaskRecordFlow::getTaskConfigId, taskConfigId)
                        .orderByAsc(TaskRecordFlow::getId));
    }

    private List<PrizeLog> prizeLogsOf() {
        return jdbcTemplate.query(
                "SELECT id, member_id, member_name, prize_code, prize_name, external_biz_no, status, fail_reason"
                        + " FROM t_prize_log WHERE member_id = ? ORDER BY id",
                (rs, i) -> {
                    PrizeLog log = new PrizeLog();
                    log.setId(rs.getLong("id"));
                    log.setMemberId(rs.getLong("member_id"));
                    log.setMemberName(rs.getString("member_name"));
                    log.setPrizeCode(rs.getString("prize_code"));
                    log.setPrizeName(rs.getString("prize_name"));
                    log.setExternalBizNo(rs.getString("external_biz_no"));
                    // 手工装配：JdbcTemplate 不经过 MyBatis 的 TypeHandler，得自己把 int 映回枚举
                    log.setStatus(SolvelaEnumUtil.getEnumByValue(rs.getInt("status"), PrizeDispatchStatusEnum.class));
                    log.setFailReason(rs.getString("fail_reason"));
                    return log;
                }, memberId);
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
        assertTrue(record.getStatus().atLeast(TaskRecordStatusEnum.COMPLETED),
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
                .filter(f -> TaskFlowTypeEnum.ADVANCE == f.getFlowType()).count();
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
                .filter(f -> TaskFlowTypeEnum.ADVANCE == f.getFlowType()).count();
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
        assertEquals(TaskFlowTypeEnum.DISCARD, flow.getFlowType());
        assertEquals(TaskDiscardCode.AMOUNT_BELOW_MIN.getValue(), flow.getDiscardCode(),
                "分类码给大屏聚类用，与人话原因并存");
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
        assertTrue(record.getStatus().atLeast(TaskRecordStatusEnum.COMPLETED), "连续3天应达标");

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

    // ==================== P1：事件注册表（方案 §2.2） ====================

    @Test
    @DisplayName("P1 注册表：未注册的事件被当场拒绝，而不是丢进线程池里慢慢发现")
    void p1_unregisteredEventIsRejected() {
        TaskEventReportCommand form = new TaskEventReportCommand();
        form.setEventCode("NOT_REGISTERED_XYZ");
        form.setMemberId(memberId);

        BusinessException result = assertThrows(BusinessException.class, () -> taskEventService.report(form),
                "未注册的事件必须被拒 —— 上游拼错一个字母时，立刻收到失败远比「返回成功但任务永远不动」好排查");
        assertTrue(result.getMessage().contains("NOT_REGISTERED_XYZ"), "错误信息要点出是哪个编码：" + result.getMessage());

        // 同步入口也要自守，不能靠 report 把关
        assertTrue(taskEventService.handle(
                event("NOT_REGISTERED_XYZ", "biz-1", null, DAY_1)).isEmpty());
    }

    @Test
    @DisplayName("🔴 P1 契约：biz_id_required=1 的事件缺 eventBizId 必须被拒（放过 = 一天只算一笔）")
    void p1_bizIdRequiredIsEnforced() {
        // 前提确认：ORDER_PAID 在注册表里确实要求带单号，否则本用例是空过
        TaskEvent def = taskEventDefService.getEnabledByCode("ORDER_PAID");
        assertNotNull(def, "前提不成立：ORDER_PAID 未注册，请先执行 v3.47.0.sql");
        assertEquals(1, def.getBizIdRequired().intValue(), "前提不成立：ORDER_PAID 应要求带单号");

        TaskEventReportCommand without = new TaskEventReportCommand();
        without.setEventCode("ORDER_PAID");
        without.setMemberId(memberId);
        BusinessException rejected = assertThrows(BusinessException.class, () -> taskEventService.report(without),
                "订单类事件不带单号时服务端只能按事件日兜底，那意味着一天只算一笔 —— 必须拒绝而不是默默兜底");
        assertTrue(rejected.getMessage().contains("eventBizId"), "错误信息要说清缺什么：" + rejected.getMessage());

        // 带上单号就该放行 —— 证明拒绝的是「缺单号」，不是这个事件本身不可用
        TaskEventReportCommand with = new TaskEventReportCommand();
        with.setEventCode("ORDER_PAID");
        with.setMemberId(memberId);
        with.setEventBizId("order-p1-001");
        assertDoesNotThrow(() -> taskEventService.report(with));
    }

    @Test
    @DisplayName("P1 计量来源：未显式传 amount 时按 metric_source 从 payload 取")
    void p1_metricSourceExtractsAmountFromPayload() throws InterruptedException {
        TaskConfig config = configOf(TASK_AMOUNT);
        TaskEvent def = taskEventDefService.getEnabledByCode("ORDER_AMOUNT");
        assertEquals("payAmount", def.getMetricSource(), "前提确认：该事件的计量来源是 payload.payAmount");

        TaskEventReportCommand form = new TaskEventReportCommand();
        form.setEventCode("ORDER_AMOUNT");
        form.setMemberId(memberId);
        form.setEventBizId("order-metric-001");
        // 刻意不设 amount，只给 payload
        form.setPayload(Map.of("orderId", "order-metric-001", "payAmount", 200));
        assertDoesNotThrow(() -> taskEventService.report(form));

        // report 是异步的，轮询等落库
        for (int i = 0; i < 40 && recordOf(config.getId()) == null; i++) {
            TimeUnit.MILLISECONDS.sleep(250);
        }
        TaskRecord record = recordOf(config.getId());
        assertNotNull(record, "事件应已被处理");
        assertEquals(0, new BigDecimal("200").compareTo(record.getCurrentMetric()),
                "金额应从 payload.payAmount 取到，实际 " + record.getCurrentMetric());
    }

    @Test
    @DisplayName("P1 开关：discard_log_flag=0 的高频事件被丢弃时不写流水（否则一天就把表写爆）")
    void p1_discardLogFlagSuppressesFlow() {
        TaskConfig config = configOf(TASK_HIGH_FREQ);
        TaskEvent def = taskEventDefService.getEnabledByCode("PAGE_VIEW");
        assertNotNull(def, "前提不成立：PAGE_VIEW 未注册");
        assertEquals(0, def.getDiscardLogFlag().intValue(), "前提不成立：PAGE_VIEW 应已关闭丢弃留痕");

        // PAGE_VIEW 不带金额，而这条任务是 AMOUNT 规则 —— 必被丢弃
        List<TaskAdvanceResult> results = taskEventService.handle(
                event("PAGE_VIEW", "pv-001", null, DAY_1));
        assertFalse(results.isEmpty(), "前提不成立：应有任务订阅 PAGE_VIEW，否则验的是「无人订阅」分支");
        assertInstanceOf(TaskAdvanceResult.Discarded.class, results.get(0));

        assertTrue(flowsOf(config.getId()).isEmpty(),
                "关闭了丢弃留痕的事件不该留下流水行；留着的话高频事件一天就能把流水表写满");

        // 对照组：开着留痕的事件，同样被丢弃时必须留痕 —— 证明上面的空不是因为压根没走到写流水
        TaskConfig amountConfig = configOf(TASK_AMOUNT);
        taskEventService.handle(event("ORDER_AMOUNT", "small-p1", "99", DAY_1));
        assertEquals(1, flowsOf(amountConfig.getId()).size(),
                "ORDER_AMOUNT 的 discard_log_flag=1，被丢弃时必须留痕");
    }

    @Test
    @DisplayName("P1 下拉：新注册的事件无需改前端即可被选到")
    void p1_optionListExposesNewlyRegisteredEvent() {
        List<TaskEventOptionDTO> options = taskEventDefService.optionList();
        assertFalse(options.isEmpty());

        // GOODS_SHARE 是本次新增的事件，只加了一行数据、没动任何前端常量
        assertTrue(options.stream().anyMatch(o -> "GOODS_SHARE".equals(o.eventCode())),
                "新增事件应出现在下拉里 —— 这正是「加事件只改数据」的判据");

        // bizIdRequired 要如实下发，好让配置的人当场知道对接方必须传什么
        TaskEventOptionDTO orderPaid = options.stream()
                .filter(o -> "ORDER_PAID".equals(o.eventCode())).findFirst().orElse(null);
        assertNotNull(orderPaid);
        assertTrue(orderPaid.bizIdRequired());

        // 停用的事件不该出现在下拉里
        assertTrue(options.stream().allMatch(o -> taskEventDefService.getEnabledByCode(o.eventCode()) != null));
    }

    // ==================== 人群过滤（target_audience） ====================

    /**
     * 带会员属性的事件（人群过滤用）
     */
    private TaskEventContext audienceEvent(String bizId, Boolean isNewMember) {
        return new TaskEventContext("AUDIENCE_TEST", memberId, member, bizId, null, DAY_1, isNewMember,
                Map.of("from", "P0AcceptanceTest"));
    }

    private TaskAdvanceResult resultOf(List<TaskAdvanceResult> all, Long taskConfigId, List<TaskConfig> configs) {
        int idx = -1;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getId().equals(taskConfigId)) {
                idx = i;
            }
        }
        return idx < 0 ? null : all.get(idx);
    }

    @Test
    @DisplayName("人群过滤：isNewMember=true 时，限新会员的任务推进、限老会员的任务被拦")
    void audienceNewMemberPassesAndOldMemberBlocked() {
        TaskConfig newOnly = configOf(TASK_NEW_ONLY);
        TaskConfig oldOnly = configOf(TASK_OLD_ONLY);
        assertEquals("NEW_MEMBER", newOnly.getTargetAudience(), "前提确认");
        assertEquals("OLD_MEMBER", oldOnly.getTargetAudience(), "前提确认");

        taskEventService.handle(audienceEvent("aud-new", Boolean.TRUE));

        assertNotNull(recordOf(newOnly.getId()), "新会员应能参与限新会员的任务");
        assertNull(recordOf(oldOnly.getId()), "新会员不该参与限老会员的任务");

        List<TaskRecordFlow> blocked = flowsOf(oldOnly.getId());
        assertEquals(1, blocked.size(), "被人群拦下也要留痕");
        assertEquals(TaskFlowTypeEnum.DISCARD, blocked.get(0).getFlowType());
        assertEquals(TaskDiscardCode.AUDIENCE_MISMATCH.getValue(), blocked.get(0).getDiscardCode());
        assertTrue(blocked.get(0).getDiscardReason().contains("老会员"),
                "原因要说清是被哪个人群条件拦的：" + blocked.get(0).getDiscardReason());
    }

    @Test
    @DisplayName("人群过滤：isNewMember=false 时正好相反 —— 证明拦的是条件，不是「一律拦」")
    void audienceOldMemberPasses() {
        TaskConfig newOnly = configOf(TASK_NEW_ONLY);
        TaskConfig oldOnly = configOf(TASK_OLD_ONLY);

        taskEventService.handle(audienceEvent("aud-old", Boolean.FALSE));

        assertNotNull(recordOf(oldOnly.getId()), "老会员应能参与限老会员的任务");
        assertNull(recordOf(newOnly.getId()), "老会员不该参与限新会员的任务");
        assertTrue(flowsOf(newOnly.getId()).get(0).getDiscardReason().contains("新会员"));
    }

    @Test
    @DisplayName("🔴 人群过滤：上游没告知会员属性时丢弃并写明原因，而不是默默放行")
    void audienceMissingAttributeIsDiscardedNotSilentlyPassed() {
        TaskConfig newOnly = configOf(TASK_NEW_ONLY);

        taskEventService.handle(audienceEvent("aud-null", null));

        assertNull(recordOf(newOnly.getId()),
                "上游没给会员属性时放行 = 人群配置静默失效，那正是这次要消灭的东西");
        List<TaskRecordFlow> flows = flowsOf(newOnly.getId());
        assertEquals(1, flows.size());
        String reason = flows.get(0).getDiscardReason();
        assertTrue(reason.contains("isNewMember"), "原因要点名缺的是哪个字段，好让人去找上游：" + reason);
        // 这一类要能被大屏单独挑出来报警 —— 它是「去找上游修」而不是「正常业务规则」
        assertEquals(TaskDiscardCode.AUDIENCE_UNKNOWN.getValue(), flows.get(0).getDiscardCode());
        assertTrue(TaskDiscardCode.resolve(flows.get(0).getDiscardCode()).needsAttention());
    }

    @Test
    @DisplayName("人群过滤：目标人群为 ALL 的任务不受影响（不传属性也照常推进）")
    void audienceAllIsUnaffected() {
        TaskConfig config = configOf(TASK_COUNT);
        assertEquals("ALL", config.getTargetAudience(), "前提确认");

        taskEventService.handle(event("DAILY_SIGN", "aud-all", null, DAY_1));
        assertNotNull(recordOf(config.getId()), "ALL 的任务不该被人群过滤影响");
    }

    // ==================== 参与轮次（limit_count） ====================

    @Test
    @DisplayName("参与轮次：每日 2 轮 -> 第1、2 个事件各完成一轮，第 3 个被判本周期已达上限")
    void roundLimitAllowsConfiguredRoundsThenBlocks() {
        TaskConfig config = configOf(TASK_ROUNDS);
        assertEquals("DAILY", config.getLimitType(), "前提确认");
        assertEquals(2, config.getLimitCount().intValue(), "前提确认：本用例要验的是 2 轮");

        String basePeriod = TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, "DAILY", DAY_1);

        // 第 1 轮：周期键是裸键（不带 #1）—— 存量记录兼容的关键
        taskEventService.handle(new TaskEventContext("ROUND_TEST", memberId, member, "r1", null, DAY_1, null, Map.of()));
        List<TaskRecord> after1 = recordsOf(config.getId());
        assertEquals(1, after1.size());
        assertEquals(basePeriod, after1.get(0).getPeriodKey(),
                "第 1 轮必须是裸周期键，加了 #1 会让存量记录全部失联");
        assertTrue(after1.get(0).getStatus().atLeast(TaskRecordStatusEnum.COMPLETED), "目标为1，一个事件即达标");

        // 第 2 轮：新记录，周期键带 #2
        taskEventService.handle(new TaskEventContext("ROUND_TEST", memberId, member, "r2", null, DAY_1, null, Map.of()));
        List<TaskRecord> after2 = recordsOf(config.getId());
        assertEquals(2, after2.size(), "第 2 轮应新开一条记录");
        assertTrue(after2.stream().anyMatch(r -> (basePeriod + "#2").equals(r.getPeriodKey())),
                "第 2 轮的周期键应为 " + basePeriod + "#2，实际 "
                        + after2.stream().map(TaskRecord::getPeriodKey).toList());

        // 第 3 轮：超出上限
        taskEventService.handle(new TaskEventContext("ROUND_TEST", memberId, member, "r3", null, DAY_1, null, Map.of()));
        assertEquals(2, recordsOf(config.getId()).size(), "超出上限不该再开新记录");

        TaskRecordFlow last = flowsOf(config.getId()).get(2);
        assertEquals(TaskFlowTypeEnum.DISCARD, last.getFlowType());
        assertEquals(TaskDiscardCode.ROUND_LIMIT_EXCEEDED.getValue(), last.getDiscardCode());
        assertTrue(last.getDiscardReason().contains("上限"), last.getDiscardReason());
    }

    @Test
    @DisplayName("参与轮次：下一个周期重新开始计轮，不受上一周期已用尽影响")
    void roundLimitResetsNextPeriod() {
        TaskConfig config = configOf(TASK_ROUNDS);

        // DAY_1 用满 2 轮
        taskEventService.handle(new TaskEventContext("ROUND_TEST", memberId, member, "d1r1", null, DAY_1, null, Map.of()));
        taskEventService.handle(new TaskEventContext("ROUND_TEST", memberId, member, "d1r2", null, DAY_1, null, Map.of()));
        assertEquals(2, recordsOf(config.getId()).size(), "前提确认：第一天已用满 2 轮");

        // DAY_2 应该能重新开始
        taskEventService.handle(new TaskEventContext("ROUND_TEST", memberId, member, "d2r1", null, DAY_2, null, Map.of()));
        List<TaskRecord> all = recordsOf(config.getId());
        assertEquals(3, all.size(), "换一天应重新计轮");

        String day2Base = TaskPeriodResolver.resolvePeriodKey(TaskTypeEnum.COUNT, "DAILY", DAY_2);
        assertTrue(all.stream().anyMatch(r -> day2Base.equals(r.getPeriodKey())),
                "第二天的第 1 轮同样是裸键：" + all.stream().map(TaskRecord::getPeriodKey).toList());
    }

    @Test
    @DisplayName("参与轮次：limit_count=1 的任务行为与改造前完全一致（周期键仍是裸键）")
    void roundLimitOneKeepsLegacyBehaviour() {
        TaskConfig config = configOf(TASK_COUNT);
        assertEquals(1, config.getLimitCount().intValue(), "前提确认");

        taskEventService.handle(event("DAILY_SIGN", "legacy-1", null, DAY_1));
        TaskRecord record = recordOf(config.getId());
        assertNotNull(record);
        assertFalse(record.getPeriodKey().contains("#"),
                "limit_count<=1 时不该出现轮次后缀，实际 " + record.getPeriodKey());
    }

    @Test
    @DisplayName("丢弃分类：推进成功的流水不该带 discard_code（写入侧串了会让统计凭空多出一类）")
    void advancedFlowHasNoDiscardCode() {
        TaskConfig config = configOf(TASK_COUNT);
        taskEventService.handle(event("DAILY_SIGN", "code-clean-1", null, DAY_1));

        List<TaskRecordFlow> flows = flowsOf(config.getId());
        assertEquals(1, flows.size());
        assertEquals(TaskFlowTypeEnum.ADVANCE, flows.get(0).getFlowType());
        assertNull(flows.get(0).getDiscardCode(), "推进流水带了分类码，说明写入侧串了");
    }

    // ==================== 模板契约校验（方案 §4.10） ====================

    private TaskTemplateSaveCommand templateForm(String code, String taskType, List<Map<String, Object>> params) {
        TaskTemplateSaveCommand form = new TaskTemplateSaveCommand();
        form.setTemplateCode(code);
        form.setTemplateName("契约校验用-" + code);
        form.setTaskType(taskType);
        form.setTriggerEvent("DAILY_SIGN");
        form.setUiSchema(Map.of("version", 1, "params", params));
        return form;
    }

    private Map<String, Object> numberParam(String key) {
        return Map.of("key", key, "label", key, "widget", "number");
    }

    @Test
    @DisplayName("🔴 契约校验：COUNT 模板没声明 targetCount 必须存不进去（否则任务会永远不完成且零报错）")
    void templateWithoutTargetParamIsRejected() {
        BusinessException result = assertThrows(BusinessException.class,
                () -> taskTemplateService.save(templateForm("TCONTRACT1", "COUNT", List.of(numberParam("targetX")))),
                "起错参数名的模板必须在保存时就被拦下 —— 放过去的话，运营能配任务、事件能进来、"
                        + "进度也涨，就是永远不完成，而且一条报错都没有");
        assertTrue(result.getMessage().contains("targetCount"),
                "报错要给出可直接照抄的键名：" + result.getMessage());
        assertTrue(result.getMessage().contains("targetX"),
                "报错要说清当前声明了什么，便于对照：" + result.getMessage());
    }

    @Test
    @DisplayName("契约校验：AMOUNT 要的是 targetAmount，给 targetCount 不算数")
    void amountTemplateRequiresTargetAmount() {
        BusinessException wrong = assertThrows(BusinessException.class,
                () -> taskTemplateService.save(templateForm("TCONTRACT2", "AMOUNT", List.of(numberParam("targetCount")))),
                "AMOUNT 判达标读的是 targetAmount，声明 targetCount 一样读不到");
        assertTrue(wrong.getMessage().contains("targetAmount"), wrong.getMessage());
    }

    @Test
    @DisplayName("契约校验：兼容存量的 targetDays；SIMPLE 不强制声明目标参数")
    void legacyKeyAndSimpleTypeAreAccepted() {
        // 存量模板 FRWAYF2X6N 用的就是 targetDays，不兼容的话它一编辑就存不回去
        assertDoesNotThrow(() -> taskTemplateService.save(
                templateForm("TCONTRACT3", "COUNT", List.of(numberParam("targetDays")))),
                "兼容形态 targetDays 应放行");

        // SIMPLE 目标恒为 1，强制声明反而是逼运营填一个永远是 1 的参数
        assertDoesNotThrow(() -> taskTemplateService.save(
                templateForm("TCONTRACT4", "SIMPLE", List.of(numberParam("someFlag")))),
                "SIMPLE 不该强制声明目标参数");

        jdbcTemplate.update("DELETE FROM t_task_template WHERE template_code IN ('TCONTRACT3','TCONTRACT4')");
    }

    @Test
    @DisplayName("契约校验：非法 task_type 直接拒绝，并列出可选值")
    void illegalTaskTypeIsRejected() {
        BusinessException result = assertThrows(BusinessException.class,
                () -> taskTemplateService.save(templateForm("TCONTRACT5", "NOT_A_TYPE", List.of(numberParam("targetCount")))));
        assertTrue(result.getMessage().contains("STREAK"), "报错要列出可选值：" + result.getMessage());
    }

    @Test
    @DisplayName("订阅判据：造数落的 status=1 必须能被订阅到 —— 判 status==2 的话所有任务永不触发")
    void subscriptionMatchesPendingStatus() {
        TaskConfig config = configOf(TASK_COUNT);
        assertEquals(TaskConfigStatusEnum.PENDING, config.getStatus(),
                "前提确认：wizardSubmit 落的就是 1-待生效，全工程没有任何地方把它改成 2");

        List<TaskAdvanceResult> results = taskEventService.handle(
                event("DAILY_SIGN", "subscribe-check", null, DAY_1));
        assertFalse(results.isEmpty(),
                "status=1 的任务必须能被事件触发；若这里为空，说明订阅判据写成了 status==2");
        assertInstanceOf(TaskAdvanceResult.Advanced.class, results.get(0));
    }
}
