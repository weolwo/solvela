package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.MemberOperationLimitStatusEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;
import solvela.member.MemberOperationLimit;
import solvela.member.operationlimit.dao.MemberOperationLimitDao;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 操作限制在真库上走一遍：落冻结中 → 解冻 / 到期回写 → 读回实体。
 *
 * <p>{@code MemberOperationLimitStatusSqlTest} 只渲染注解 SQL、不连库；这里验的是 {@code ${...}} 在 Spring 装配的
 * MyBatis-Plus 配置下能求值并真的命中行、create_time 由 DDL 填，以及人工解冻与到期回写撞车时第二次不覆盖第一次。
 * 数据由用例自己造，结束时回滚。会员号用一个不存在的大数，不碰库里已有的限制。
 */
@SpringBootTest
@Transactional
class MemberOperationLimitStatusFlowTest {

    private static final long MEMBER_ID = 990000000001L;

    @Autowired
    private MemberOperationLimitDao dao;

    @Test
    @DisplayName("🔴 人工解冻之后，到期回写不覆盖它记的解冻方式与操作人；再解一次影响 0 行")
    void 人工解冻不被到期回写覆盖() {
        LocalDateTime now = LocalDateTime.now();
        MemberOperationLimit limit = insert(now.minusHours(2), now.minusHours(1));
        MemberOperationLimit read = dao.selectById(limit.getId());
        assertAll(
                () -> assertEquals(MemberOperationLimitStatusEnum.LOCKED, read.getStatus(), "落库后读回的状态不对，或没装配上"),
                () -> assertNotNull(read.getCreateTime(), "create_time 应由 DDL 填"));

        assertEquals(1, dao.unlock(MEMBER_ID, MemberOperationTypeEnum.LOGIN, now,
                MemberOperationUnlockTypeEnum.MANUAL, "客服A", "核实本人"));
        assertEquals(0, dao.unlock(MEMBER_ID, MemberOperationTypeEnum.LOGIN, now,
                MemberOperationUnlockTypeEnum.MANUAL, "客服B", "重复点击"));
        dao.settleExpired(now);

        MemberOperationLimit after = dao.selectById(limit.getId());
        assertAll(
                () -> assertEquals(MemberOperationLimitStatusEnum.UNLOCKED, after.getStatus()),
                () -> assertEquals(MemberOperationUnlockTypeEnum.MANUAL, after.getUnlockType(), "到期回写盖掉了人工解冻"),
                () -> assertEquals("客服A", after.getOperator()));
    }

    @Test
    @DisplayName("到期回写：已过期的推到已解冻、记自动到期；没过期的不动")
    void 到期回写() {
        LocalDateTime now = LocalDateTime.now();
        MemberOperationLimit expired = insert(now.minusHours(2), now.minusHours(1));
        MemberOperationLimit active = insert(now, now.plusHours(1));

        dao.settleExpired(now);

        MemberOperationLimit afterExpired = dao.selectById(expired.getId());
        MemberOperationLimit afterActive = dao.selectById(active.getId());
        assertAll(
                () -> assertEquals(MemberOperationLimitStatusEnum.UNLOCKED, afterExpired.getStatus()),
                () -> assertEquals(MemberOperationUnlockTypeEnum.AUTO_EXPIRE, afterExpired.getUnlockType()),
                () -> assertEquals(MemberOperationLimitStatusEnum.LOCKED, afterActive.getStatus()),
                () -> assertNull(afterActive.getUnlockType()),
                () -> assertEquals(active.getId(),
                        dao.selectActive(MEMBER_ID, MemberOperationTypeEnum.LOGIN, now).getId(), "生效中的应是没过期那条"));
    }

    private MemberOperationLimit insert(LocalDateTime lockTime, LocalDateTime expireTime) {
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.setMemberId(MEMBER_ID);
        limit.setOperationType(MemberOperationTypeEnum.LOGIN);
        limit.setReason("状态机验收");
        limit.lockUntil(lockTime, expireTime);
        dao.insert(limit);
        return limit;
    }
}
