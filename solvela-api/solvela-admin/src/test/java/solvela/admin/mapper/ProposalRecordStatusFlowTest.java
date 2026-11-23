package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.PrizeTypeEnum;
import solvela.enums.ProposalSourceTypeEnum;
import solvela.enums.ProposalStatusEnum;
import solvela.risk.ProposalRecord;
import solvela.risk.proposal.dao.ProposalRecordDao;

import java.math.BigDecimal;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 提案状态在真库上走一遍：落初始状态 → 条件 UPDATE → 读回实体。
 *
 * <p>{@code ProposalRecordStatusSqlTest} 只渲染 SQL、不连库；这里验的是 {@code ${...}} 在 Spring 装配的
 * MyBatis-Plus 配置下能求值并真的命中行，去掉 setter 之后状态与拦截分类仍被装配进来，
 * 以及闸门只放行一次。数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class ProposalRecordStatusFlowTest {

    @Autowired
    private ProposalRecordDao proposalRecordDao;

    @Test
    @DisplayName("🔴 免审：待执行 → 执行中只放行一次 → 成功；终态之后失败回写影响 0 行")
    void 免审下发() {
        ProposalRecord p = insert(ProposalRecord::awaitExecution);
        assertEquals(ProposalStatusEnum.PENDING_EXECUTE, statusOf(p), "落库后读回的状态不对，或没装配上");

        assertEquals(1, proposalRecordDao.claimForExecution(p.getId()));
        assertEquals(0, proposalRecordDao.claimForExecution(p.getId()), "同一提案被抢到两次执行权");
        assertEquals(0, proposalRecordDao.markFailedUnclaimed(p.getId(), "晚到的异常兜底"),
                "已在执行中的提案被「没抢到执行权」的兜底改成了失败");
        assertEquals(1, proposalRecordDao.markSucceeded(p.getId(), "资产下发成功"));
        assertEquals(0, proposalRecordDao.markFailed(p.getId(), "晚到的失败"));

        ProposalRecord after = proposalRecordDao.selectById(p.getId());
        assertAll(
                () -> assertEquals(ProposalStatusEnum.SUCCESS, after.getStatus()),
                () -> assertEquals("资产下发成功", after.getRemark()));
    }

    @Test
    @DisplayName("抢执行权时异常：待执行直接落失败")
    void 未抢到执行权落失败() {
        ProposalRecord p = insert(ProposalRecord::awaitExecution);
        assertEquals(1, proposalRecordDao.markFailedUnclaimed(p.getId(), "系统执行异常"));
        assertEquals(ProposalStatusEnum.FAILED, statusOf(p));
        assertEquals(0, proposalRecordDao.claimForExecution(p.getId()), "失败的提案又被抢到了执行权");
    }

    @Test
    @DisplayName("🔴 双层审批：一审转二审写一审人，二审通过写二审人；重复审批影响 0 行")
    void 双层审批() {
        ProposalRecord p = insert(ProposalRecord::awaitFirstReview);
        assertEquals(1, proposalRecordDao.escalateToSecondReview(p.getId(), "财务A", "一审同意"));
        assertEquals(0, proposalRecordDao.passFirstReview(p.getId(), "财务C", "并发的一审"));
        assertEquals(1, proposalRecordDao.passSecondReview(p.getId(), "财务B", "二审同意"));
        assertEquals(0, proposalRecordDao.rejectAtSecondReview(p.getId(), "财务D", "晚到的驳回"));

        ProposalRecord after = proposalRecordDao.selectById(p.getId());
        assertAll(
                () -> assertEquals(ProposalStatusEnum.PENDING_EXECUTE, after.getStatus()),
                () -> assertEquals("财务A", after.getFirstReviewer()),
                () -> assertNotNull(after.getFirstReviewTime()),
                () -> assertEquals("财务B", after.getSecondReviewer()),
                () -> assertNotNull(after.getSecondReviewTime()),
                () -> assertEquals("二审同意", after.getReviewComment()));
    }

    @Test
    @DisplayName("一审驳回之后不能再审批、不能执行")
    void 一审驳回() {
        ProposalRecord p = insert(ProposalRecord::awaitFirstReview);
        assertEquals(1, proposalRecordDao.rejectAtFirstReview(p.getId(), "财务A", "金额不对"));
        assertEquals(0, proposalRecordDao.passFirstReview(p.getId(), "财务B", "晚到的通过"));
        assertEquals(0, proposalRecordDao.claimForExecution(p.getId()));
        assertEquals(ProposalStatusEnum.REJECTED, statusOf(p));
    }

    @Test
    @DisplayName("风控拦截：状态与拦截分类都能读回")
    void 风控拦截读回() {
        ProposalRecord p = insert(r -> r.blockByRisk("GLOBAL_BUDGET_LIMIT"));
        ProposalRecord after = proposalRecordDao.selectById(p.getId());
        assertAll(
                () -> assertEquals(ProposalStatusEnum.RISK_BLOCKED, after.getStatus()),
                () -> assertEquals("GLOBAL_BUDGET_LIMIT", after.getRiskCode()));
    }

    private ProposalStatusEnum statusOf(ProposalRecord p) {
        return proposalRecordDao.selectById(p.getId()).getStatus();
    }

    private ProposalRecord insert(Consumer<ProposalRecord> initialState) {
        String no = "ZZT" + System.nanoTime();
        ProposalRecord p = new ProposalRecord();
        p.setTradeNo(no);
        p.setMemberId(940001L);
        p.setMemberName("zz-test");
        p.setAssetType(PrizeTypeEnum.SCORE);
        p.setAmount(new BigDecimal("10"));
        p.setQuantity(1);
        p.setSourceType(ProposalSourceTypeEnum.MANUAL);
        p.setSourceBizId(no);
        p.setPromotionConfigId(-1L);
        initialState.accept(p);
        proposalRecordDao.insert(p);
        return p;
    }
}
