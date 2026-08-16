package net.lab1024.sa.admin.funnel;

import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordQueryForm;
import net.lab1024.sa.risk.proposal.dao.ProposalRecordDao;
import net.lab1024.sa.risk.proposal.domain.vo.ProposalFunnelVO;
import net.lab1024.sa.risk.proposal.service.ProposalRecordService;
import net.lab1024.sa.task.record.dao.TaskRecordDao;
import net.lab1024.sa.task.record.domain.form.TaskRecordQueryForm;
import net.lab1024.sa.task.record.domain.vo.TaskRecordFunnelVO;
import net.lab1024.sa.task.record.service.TaskRecordService;
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
            assertEquals(Set.of("reason", "blockCount"), blockStat.get(0).keySet());
        }
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
                vo.getSourceList().stream().mapToLong(ProposalFunnelVO.SourceStatVO::getProposalCount).sum(),
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

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
