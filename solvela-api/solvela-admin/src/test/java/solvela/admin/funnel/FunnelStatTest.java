package solvela.admin.funnel;

import solvela.prize.prizelog.dao.PrizeLogDao;
import solvela.prize.prizelog.domain.form.PrizeLogQueryForm;
import solvela.prize.prizelog.domain.vo.PrizeLogFunnelVO;
import solvela.prize.prizelog.service.PrizeLogService;
import solvela.risk.proposal.domain.form.ProposalRecordQueryForm;
import solvela.risk.proposal.dao.ProposalRecordDao;
import solvela.risk.proposal.domain.vo.ProposalFunnelVO;
import solvela.risk.proposal.service.ProposalRecordService;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.record.domain.form.TaskRecordQueryForm;
import solvela.task.record.domain.vo.TaskRecordFunnelVO;
import solvela.task.record.service.TaskRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务漏斗 / 提案漏斗验收（真跑数据库，不是推断）。
 *
 * <h3>为什么要有这个测试：漏斗的错法是「静默算成 0」</h3>
 * 漏斗的取数链路是 {@code SQL 别名 -> Map<String,Object> -> VO}，中间靠<b>字符串 key</b> 对接。
 * key 打错一个字母，{@code row.get("xxx")} 返回 null，兜底成 0，页面上就是一个安安静静的
 * 「0 条异常」—— 编译不报错、SQL 不报错、接口 200，而运营看到的是「一切正常」。
 * 这正是本项目反复强调的「前提不成立时，通过和空过分不出来」。
 *
 * <p>所以断言分两层，缺一不可：
 * <ol>
 *   <li><b>key 契约</b>：直接比对 DAO 返回的 Map 的 keySet 与 Service 读取的那批名字。
 *       这一层<b>不依赖库里有没有数据</b> —— 全表为空时它照样能抓出拼写错误；</li>
 *   <li><b>数值自洽</b>：分项之和 == 总数、分布表条数之和 == 总数。
 *       这一层能抓出「漏了一个状态桶」这种 SQL 遗漏。</li>
 * </ol>
 *
 * <p>本测试<b>只读</b>：不造数、不改库，可以随时重复跑。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class FunnelStatTest {

    @Autowired
    private TaskRecordService taskRecordService;
    @Autowired
    private TaskRecordDao taskRecordDao;
    @Autowired
    private ProposalRecordService proposalRecordService;
    @Autowired
    private ProposalRecordDao proposalRecordDao;
    @Autowired
    private PrizeLogService prizeLogService;
    @Autowired
    private PrizeLogDao prizeLogDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * TaskRecordService.funnel 会从 Map 里取的全部 key。改 SQL 别名时这里必须同步改，
     * 否则测试红 —— 这正是要的效果。
     */
    private static final Set<String> TASK_FUNNEL_KEYS = Set.of(
            "totalCount", "memberCount", "runningCount", "completedCount", "dispatchedCount",
            "expiredCount", "staleRunningCount", "reachedNoCompleteTime", "runningWithCompleteTime",
            "reachedNoPrizeSnapshot", "noRuleSnapshot");

    private static final Set<String> PROPOSAL_FUNNEL_KEYS = Set.of(
            "totalCount", "memberCount", "waitingCount", "firstReviewCount", "secondReviewCount",
            "rejectedCount", "pendingExecuteCount", "executingCount", "successCount", "partialCount",
            "failedCount", "blockedCount", "pendingReviewOldestMinutes", "stuckDispatchCount",
            "sameReviewerCount", "rejectNoCommentCount", "reviewerNoTimeCount");

    private static final Set<String> PRIZE_FUNNEL_KEYS = Set.of(
            "totalCount", "memberCount", "successCount", "waitingCount", "failedCount",
            "approveNoneCount", "approvePendingCount", "approvePassedCount", "approveRejectedCount",
            "approveOldestMinutes", "stuckWaitingCount", "rejectedButSentCount", "failedNoReasonCount",
            "successWithFailReasonCount", "noExternalBizNoCount", "badValueCount", "expiredWaitingCount");

    // ==================== 任务漏斗 ====================

    @Test
    @DisplayName("任务漏斗：SQL 别名与 Service 读取的 key 完全一致（拼错一个字母就会静默变 0）")
    void taskFunnelKeysMatch() {
        Map<String, Object> row = taskRecordDao.selectFunnel(new TaskRecordQueryForm());
        assertNotNull(row, "selectFunnel 不该返回 null：它是聚合查询，空表也会有一行");
        assertEquals(TASK_FUNNEL_KEYS, row.keySet(),
                "SQL 别名与 Service 读取的 key 对不上：多出来的是白算的，少的那个会被兜底成 0");

        // 分布查询的列名同样是 key 契约
        List<Map<String, Object>> taskStat = taskRecordDao.selectTaskStat(new TaskRecordQueryForm());
        if (!taskStat.isEmpty()) {
            assertEquals(Set.of("taskConfigId", "recordCount", "memberCount", "reachedCount", "staleRunningCount"),
                    taskStat.get(0).keySet());
        }
        List<Map<String, Object>> discardStat = taskRecordDao.selectDiscardStat(new TaskRecordQueryForm());
        if (!discardStat.isEmpty()) {
            assertEquals(Set.of("discardCode", "discardCount"), discardStat.get(0).keySet());
        }
    }

    @Test
    @DisplayName("任务漏斗：总数与四个状态桶之和自洽，且与库里的真实条数一致")
    void taskFunnelNumbersAddUp() {
        long dbTotal = count("SELECT COUNT(*) FROM t_task_record");
        // 铁律 16：先确认前提成立，否则「全 0 也通过」的空过与真通过分不出来
        assertTrue(dbTotal > 0, "t_task_record 没有数据，本用例无法证明任何事 —— 先造数再跑");

        TaskRecordFunnelVO vo = taskRecordService.funnel(new TaskRecordQueryForm());
        assertEquals(dbTotal, vo.getTotalCount());
        assertEquals(vo.getTotalCount(),
                vo.getRunningCount() + vo.getCompletedCount() + vo.getDispatchedCount() + vo.getExpiredCount(),
                "四个状态桶之和不等于总数：要么 SQL 漏了一个 status 取值，要么库里有字典外的状态");

        long statSum = vo.getTaskList().stream().mapToLong(TaskRecordFunnelVO.TaskStatVO::getRecordCount).sum();
        long top20Total = count("SELECT COALESCE(SUM(c), 0) FROM (SELECT COUNT(*) c FROM t_task_record"
                + " GROUP BY task_config_id ORDER BY c DESC LIMIT 20) t");
        assertEquals(top20Total, statSum, "任务分布（TOP 20）条数之和与库里对不上");

        long discardSum = vo.getDiscardList().stream()
                .mapToLong(TaskRecordFunnelVO.DiscardStatVO::getDiscardCount).sum();
        assertEquals(count("SELECT COUNT(*) FROM t_task_record_flow WHERE flow_type = 2"), discardSum,
                "丢弃分类条数之和与流水表对不上");
        assertEquals(discardSum, vo.getDiscardTotalCount());
    }

    // ==================== 提案漏斗 ====================

    @Test
    @DisplayName("提案漏斗：SQL 别名与 Service 读取的 key 完全一致")
    void proposalFunnelKeysMatch() {
        Map<String, Object> row = proposalRecordDao.selectFunnel(new ProposalRecordQueryForm());
        assertNotNull(row);
        assertEquals(PROPOSAL_FUNNEL_KEYS, row.keySet());

        List<Map<String, Object>> assetStat = proposalRecordDao.selectAssetStat(new ProposalRecordQueryForm());
        if (!assetStat.isEmpty()) {
            assertEquals(Set.of("assetType", "proposalCount", "successCount",
                            "successAmount", "pendingAmount", "blockedAmount"),
                    assetStat.get(0).keySet());
        }
        List<Map<String, Object>> sourceStat = proposalRecordDao.selectSourceStat(new ProposalRecordQueryForm());
        if (!sourceStat.isEmpty()) {
            assertEquals(Set.of("sourceType", "proposalCount", "successCount"), sourceStat.get(0).keySet());
        }
        List<Map<String, Object>> blockStat = proposalRecordDao.selectBlockReasonStat(new ProposalRecordQueryForm());
        if (!blockStat.isEmpty()) {
            assertEquals(Set.of("riskCode", "sampleRemark", "blockCount"), blockStat.get(0).keySet());
        }
    }

    @Test
    @DisplayName("提案漏斗：拦截原因按 risk_code 聚类，且条数之和 = 风控拦截总数")
    void proposalBlockReasonGroupedByCode() {
        long blocked = count("SELECT COUNT(*) FROM t_proposal_record WHERE status = 80");
        assertTrue(blocked > 0, "库里没有被风控拦截的提案，本用例无法证明任何事 —— 先造数再跑");

        ProposalFunnelVO vo = proposalRecordService.funnel(new ProposalRecordQueryForm());
        assertEquals(blocked, vo.getBlockedCount());
        assertEquals(blocked,
                vo.getBlockReasonList().stream().mapToLong(ProposalFunnelVO.BlockReasonVO::getBlockCount).sum(),
                "拦截原因条数之和与拦截总数对不上（分类超过 10 类时 LIMIT 10 会截断，届时本断言需要跟着改）");

        /*
         * v3.68.0 之后，被拦截的提案必须带得上分类：全是 null 说明迁移没跑，
         * 或者写入侧没把 RiskResult.ruleCode 传下来 —— 而那种情况下漏斗看起来仍然「正常」
         * （只是所有拦截都挤进「未归类」一条），正是要在这里拦住的。
         */
        assertTrue(vo.getBlockReasonList().stream().anyMatch(item -> item.getRiskCode() != null),
                "所有拦截行的 risk_code 都是空的：v3.68.0.sql 没执行，或写入侧没落编码");
    }

    @Test
    @DisplayName("提案漏斗：十个状态桶之和 = 总数 = 资产分布之和 = 来源分布之和")
    void proposalFunnelNumbersAddUp() {
        long dbTotal = count("SELECT COUNT(*) FROM t_proposal_record");
        assertTrue(dbTotal > 0, "t_proposal_record 没有数据，本用例无法证明任何事 —— 先造数再跑");

        ProposalFunnelVO vo = proposalRecordService.funnel(new ProposalRecordQueryForm());
        assertEquals(dbTotal, vo.getTotalCount());

        long bucketSum = vo.getWaitingCount() + vo.getFirstReviewCount() + vo.getSecondReviewCount()
                + vo.getRejectedCount() + vo.getPendingExecuteCount() + vo.getExecutingCount()
                + vo.getSuccessCount() + vo.getPartialCount() + vo.getFailedCount() + vo.getBlockedCount();
        assertEquals(vo.getTotalCount(), bucketSum,
                "状态桶之和不等于总数：库里出现了 0/10/11/20/30/40/50/60/70/80 之外的状态");

        assertEquals(vo.getTotalCount(),
                vo.getAssetList().stream().mapToLong(ProposalFunnelVO.AssetStatVO::getProposalCount).sum(),
                "资产分布条数之和不等于总数");
        assertEquals(vo.getTotalCount(),
                vo.getSourceList().stream().mapToLong(ProposalFunnelVO.SourceStatDTO::getProposalCount).sum(),
                "来源分布条数之和不等于总数");
        assertEquals(vo.getFirstReviewCount() + vo.getSecondReviewCount(), vo.getPendingReviewCount());
    }

    @Test
    @DisplayName("提案漏斗：金额按资产类型分开算，且只统计成功的那部分")
    void proposalFunnelAmountIsPerAssetType() {
        ProposalFunnelVO vo = proposalRecordService.funnel(new ProposalRecordQueryForm());
        assertFalse(vo.getAssetList().isEmpty(), "t_proposal_record 没有数据，本用例无法证明任何事");

        for (ProposalFunnelVO.AssetStatVO asset : vo.getAssetList()) {
            Double expected = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount * quantity), 0) FROM t_proposal_record"
                            + " WHERE asset_type = ? AND status = 50", Double.class, asset.getAssetType());
            assertEquals(expected, asset.getSuccessAmount().doubleValue(), 0.001,
                    "资产 " + asset.getAssetType() + " 的已发出金额与库里对不上");
        }
    }

    // ==================== 奖励漏斗 ====================

    @Test
    @DisplayName("奖励漏斗：SQL 别名与 Service 读取的 key 完全一致")
    void prizeFunnelKeysMatch() {
        Map<String, Object> row = prizeLogDao.selectFunnel(new PrizeLogQueryForm());
        assertNotNull(row, "selectFunnel 不该返回 null：它是聚合查询，空表也会有一行");
        assertEquals(PRIZE_FUNNEL_KEYS, row.keySet(),
                "SQL 别名与 Service 读取的 key 对不上：多出来的是白算的，少的那个会被兜底成 0");

        List<Map<String, Object>> typeStat = prizeLogDao.selectPrizeTypeStat(new PrizeLogQueryForm());
        if (!typeStat.isEmpty()) {
            assertEquals(Set.of("prizeType", "logCount", "successCount",
                            "successValue", "waitingValue", "failedValue", "badValueCount"),
                    typeStat.get(0).keySet());
        }
        List<Map<String, Object>> prizeStat = prizeLogDao.selectPrizeStat(new PrizeLogQueryForm());
        if (!prizeStat.isEmpty()) {
            assertEquals(Set.of("prizeCode", "prizeName", "prizeType", "logCount",
                            "successCount", "waitingCount", "failedCount", "pendingCount"),
                    prizeStat.get(0).keySet());
        }
        List<Map<String, Object>> failStat = prizeLogDao.selectFailReasonStat(new PrizeLogQueryForm());
        if (!failStat.isEmpty()) {
            assertEquals(Set.of("failReason", "failCount"), failStat.get(0).keySet());
        }
    }

    @Test
    @DisplayName("奖励漏斗：执行状态桶、审批状态桶、类型分布三者各自都要等于总数")
    void prizeFunnelNumbersAddUp() {
        long dbTotal = count("SELECT COUNT(*) FROM t_prize_log");
        // 铁律 16：先确认前提成立，否则「全 0 也通过」的空过与真通过分不出来
        assertTrue(dbTotal > 0, "t_prize_log 没有数据，本用例无法证明任何事 —— 先造数再跑");

        PrizeLogFunnelVO vo = prizeLogService.funnel(new PrizeLogQueryForm());
        assertEquals(dbTotal, vo.getTotalCount());
        assertEquals(vo.getTotalCount(),
                vo.getSuccessCount() + vo.getWaitingCount() + vo.getFailedCount(),
                "执行状态桶之和不等于总数：要么 SQL 漏了一个 status 取值，要么库里有字典外的状态");
        assertEquals(vo.getTotalCount(),
                vo.getApproveNoneCount() + vo.getApprovePendingCount()
                        + vo.getApprovePassedCount() + vo.getApproveRejectedCount(),
                "审批状态桶之和不等于总数：库里出现了 0/1/2/3 之外的 approve_status");
        assertEquals(vo.getTotalCount(),
                vo.getTypeList().stream().mapToLong(PrizeLogFunnelVO.PrizeTypeStatVO::getLogCount).sum(),
                "奖励类型分布条数之和不等于总数");

        long prizeSum = vo.getPrizeList().stream()
                .mapToLong(PrizeLogFunnelVO.PrizeStatVO::getLogCount).sum();
        long top20Total = count("SELECT COALESCE(SUM(c), 0) FROM (SELECT COUNT(*) c FROM t_prize_log"
                + " GROUP BY prize_code ORDER BY c DESC LIMIT 20) t");
        assertEquals(top20Total, prizeSum, "奖品分布（TOP 20）条数之和与库里对不上");
    }

    @Test
    @DisplayName("奖励漏斗：价值按奖励类型分开算，且只统计已发出、体值可解析的那部分")
    void prizeFunnelValueIsPerPrizeType() {
        PrizeLogFunnelVO vo = prizeLogService.funnel(new PrizeLogQueryForm());
        assertFalse(vo.getTypeList().isEmpty(), "t_prize_log 没有数据，本用例无法证明任何事");

        for (PrizeLogFunnelVO.PrizeTypeStatVO type : vo.getTypeList()) {
            /*
             * 断言里必须带上同一个 REGEXP：prize_value 是 varchar，
             * 直接 CAST 的话 MySQL 对解析不了的字符串只给警告并返回 0 ——
             * 期望值会跟着一起「静默算成 0」，于是漏算和不漏算都能通过，断言就白写了。
             */
            Double expected = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(CAST(prize_value AS DECIMAL(18,4))), 0) FROM t_prize_log"
                            + " WHERE prize_type = ? AND status = 1"
                            + " AND prize_value REGEXP '^-?[0-9]+([.][0-9]+)?$'",
                    Double.class, type.getPrizeType());
            assertEquals(expected, type.getSuccessValue().doubleValue(), 0.01,
                    "奖励类型 " + type.getPrizeType() + " 的已发出价值与库里对不上");
        }
    }

    @Test
    @DisplayName("奖励漏斗：失败原因条数之和 = 失败总数（分类超过 10 类时本断言需跟着改）")
    void prizeFailReasonAddsUp() {
        long failed = count("SELECT COUNT(*) FROM t_prize_log WHERE status = 2");
        assertTrue(failed > 0, "库里没有发放失败的记录，本用例无法证明任何事 —— 先造数再跑");

        PrizeLogFunnelVO vo = prizeLogService.funnel(new PrizeLogQueryForm());
        assertEquals(failed, vo.getFailedCount());
        assertEquals(failed,
                vo.getFailReasonList().stream().mapToLong(PrizeLogFunnelVO.FailReasonVO::getFailCount).sum(),
                "失败原因条数之和与失败总数对不上（原因超过 10 种时 LIMIT 10 会截断，届时本断言需要跟着改）");
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
