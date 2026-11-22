package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.ActivityTypeEnum;
import solvela.enums.PrizeApproveStatusEnum;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeProposalStatusEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;
import solvela.prize.prizelog.dao.PrizeLogDao;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 发奖记录派发状态在真库上走一遍：落等待 → 条件回写 → 读回实体。
 *
 * <p>{@code PrizeLogStatusSqlTest} 只渲染 SQL、不连库；这里验的是 {@code ${...}} 在 Spring 装配的
 * MyBatis-Plus 配置下能求值并真的命中行，去掉 setter 之后状态仍被装配进来，
 * 以及最要紧的一条：<b>晚到的回写不覆盖先到的终态</b>。数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class PrizeLogStatusFlowTest {

    @Autowired
    private PrizeLogDao prizeLogDao;

    @Test
    @DisplayName("🔴 资产引擎先回写了成功，处理器晚到的「已受理」不把它盖回等待")
    void 已受理不覆盖引擎终态() {
        PrizeLog log = insert();
        assertEquals(PrizeDispatchStatusEnum.WAITING, statusOf(log), "落库后读回的状态不对，或没装配上");

        assertEquals(1, prizeLogDao.updateStatusByExternalBizNo(log.getExternalBizNo(), PrizeDispatchStatusEnum.SUCCESS, null));
        assertEquals(1, prizeLogDao.markProposalAccepted(log.getId(), 777L));

        PrizeLog after = prizeLogDao.selectById(log.getId());
        assertAll(
                () -> assertEquals(PrizeDispatchStatusEnum.SUCCESS, after.getStatus(), "引擎写好的成功被盖掉了"),
                () -> assertEquals(PrizeProposalStatusEnum.ACCEPTED, after.getProposalStatus()),
                () -> assertEquals(777L, after.getProposalId()));
    }

    @Test
    @DisplayName("🔴 已经成功的记录，处理器晚到的失败回写影响 0 行")
    void 失败不覆盖成功() {
        PrizeLog log = insert();
        assertEquals(1, prizeLogDao.markDispatchSucceeded(log.getId()));
        assertEquals(0, prizeLogDao.markDispatchFailed(log.getId(), "晚到的失败", PrizeProposalStatusEnum.REJECTED));
        assertEquals(PrizeDispatchStatusEnum.SUCCESS, statusOf(log));
    }

    @Test
    @DisplayName("等待 → 失败，带上提案侧被拒；之后引擎的回写也不覆盖")
    void 失败带提案结果() {
        PrizeLog log = insert();
        assertEquals(1, prizeLogDao.markDispatchFailed(log.getId(), "预算耗尽", PrizeProposalStatusEnum.REJECTED));
        assertEquals(0, prizeLogDao.updateStatusByExternalBizNo(log.getExternalBizNo(), PrizeDispatchStatusEnum.SUCCESS, null));
        PrizeLog after = prizeLogDao.selectById(log.getId());
        assertAll(
                () -> assertEquals(PrizeDispatchStatusEnum.FAIL, after.getStatus()),
                () -> assertEquals(PrizeProposalStatusEnum.REJECTED, after.getProposalStatus()),
                () -> assertEquals("预算耗尽", after.getFailReason()));
    }

    private PrizeDispatchStatusEnum statusOf(PrizeLog log) {
        return prizeLogDao.selectById(log.getId()).getStatus();
    }

    private PrizeLog insert() {
        PrizeLog log = new PrizeLog();
        log.setMemberId(940001L);
        log.setMemberName("zz-test");
        log.setExternalBizNo("ZZTEST" + System.nanoTime());
        log.setActivityCode("ZZTESTACT");
        log.setActivityType(ActivityTypeEnum.LOTTERY);
        log.setPrizeCode("ZZPRIZE");
        log.setPrizeName("状态机验收");
        log.setPrizeType(PrizeTypeEnum.COUPON);
        log.setPrizeValue("1");
        log.setApproveStatus(PrizeApproveStatusEnum.NOT_REQUIRED);
        log.awaitDispatch();
        prizeLogDao.insert(log);
        return log;
    }
}
