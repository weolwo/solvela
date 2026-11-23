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

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(0, prizeLogDao.markProposalRejected(log.getId(), "晚到的失败"));
        assertEquals(PrizeDispatchStatusEnum.SUCCESS, statusOf(log));
    }

    @Test
    @DisplayName("等待 → 失败，带上提案侧被拒；之后引擎的回写也不覆盖")
    void 失败带提案结果() {
        PrizeLog log = insert();
        assertEquals(1, prizeLogDao.markProposalRejected(log.getId(), "预算耗尽"));
        assertEquals(0, prizeLogDao.markProposalAccepted(log.getId(), 9L), "被拒的提案又被写成了已受理");
        assertEquals(0, prizeLogDao.updateStatusByExternalBizNo(log.getExternalBizNo(), PrizeDispatchStatusEnum.SUCCESS, null));
        PrizeLog after = prizeLogDao.selectById(log.getId());
        assertAll(
                () -> assertEquals(PrizeDispatchStatusEnum.FAIL, after.getStatus()),
                () -> assertEquals(PrizeProposalStatusEnum.REJECTED, after.getProposalStatus()),
                () -> assertEquals("预算耗尽", after.getFailReason()));
    }

    @Test
    @DisplayName("🔴 对账重投只捞仍在等待、审批允许派发的；待审批、已驳回、执行异常落失败的都不捞")
    void 对账重投的取数() {
        // 造在很早以前，并且只看这段时间：不受库里其它记录影响
        LocalDateTime longAgo = LocalDateTime.of(2001, 1, 1, 0, 0);
        PrizeLog notRequired = insert(PrizeApproveStatusEnum.NOT_REQUIRED, longAgo);
        PrizeLog passed = insert(PrizeApproveStatusEnum.PASSED, longAgo);
        PrizeLog pending = insert(PrizeApproveStatusEnum.PENDING, longAgo);
        PrizeLog rejected = insert(PrizeApproveStatusEnum.REJECTED, longAgo);
        PrizeLog failed = insert(PrizeApproveStatusEnum.NOT_REQUIRED, longAgo);
        assertEquals(1, prizeLogDao.markDispatchFailed(failed.getId(), "执行异常"));
        PrizeLog accepted = insert(PrizeApproveStatusEnum.NOT_REQUIRED, longAgo);
        assertEquals(1, prizeLogDao.markProposalAccepted(accepted.getId(), 1L));

        Set<Long> picked = prizeLogDao.selectRetryable(longAgo.plusDays(1), 1000).stream()
                .map(PrizeLog::getId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(notRequired.getId(), passed.getId()), picked,
                "待审批 " + pending.getId() + "、已驳回 " + rejected.getId() + "、已失败 " + failed.getId()
                        + "、已受理 " + accepted.getId() + " 都不该被重投");
    }

    @Test
    @DisplayName("🔴 审批：待审批 → 已批准只放行一次，批准后不能再驳回；审批人与时间由闸门写")
    void 审批闸门() {
        PrizeLog log = insert(PrizeApproveStatusEnum.PENDING, null);
        assertEquals(PrizeApproveStatusEnum.PENDING, prizeLogDao.selectById(log.getId()).getApproveStatus());

        assertEquals(1, prizeLogDao.markApproved(log.getId(), "运营A"));
        assertEquals(0, prizeLogDao.markApproved(log.getId(), "运营B"), "同一条记录被批准了两次");
        assertEquals(0, prizeLogDao.markApprovalRejected(log.getId(), "运营C"));

        PrizeLog after = prizeLogDao.selectById(log.getId());
        assertAll(
                () -> assertEquals(PrizeApproveStatusEnum.PASSED, after.getApproveStatus()),
                () -> assertEquals("运营A", after.getApproveBy()),
                () -> assertNotNull(after.getApproveTime()),
                () -> assertEquals(PrizeProposalStatusEnum.PENDING, after.getProposalStatus(), "落库时提案列应是待提交"));
    }

    private PrizeDispatchStatusEnum statusOf(PrizeLog log) {
        return prizeLogDao.selectById(log.getId()).getStatus();
    }

    private PrizeLog insert() {
        return insert(PrizeApproveStatusEnum.NOT_REQUIRED, null);
    }

    /** @param createTime 为 null 时由 DDL 填当前时间 */
    private PrizeLog insert(PrizeApproveStatusEnum approve, LocalDateTime createTime) {
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
        log.setCreateTime(createTime);
        // 审批状态没有 setter：初始两种走实体方法，批准 / 驳回走真正的审批闸门
        if (approve == PrizeApproveStatusEnum.NOT_REQUIRED) {
            log.exemptFromApproval();
        } else {
            log.awaitApproval();
        }
        log.awaitDispatch();
        prizeLogDao.insert(log);
        if (approve == PrizeApproveStatusEnum.PASSED) {
            assertEquals(1, prizeLogDao.markApproved(log.getId(), "运营A"));
        } else if (approve == PrizeApproveStatusEnum.REJECTED) {
            assertEquals(1, prizeLogDao.markApprovalRejected(log.getId(), "运营A"));
        }
        return log;
    }
}
