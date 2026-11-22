package solvela.member.entitlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.biz.server.BizApplication;
import solvela.enums.EntitlementGrantStatusEnum;
import solvela.member.GradeEntitlementGrant;
import solvela.member.entitlement.dao.GradeEntitlementGrantDao;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 权益发放状态机在真库上走一遍：生成 → 条件 UPDATE 迁移 → 读回实体。
 *
 * <p>{@code EntitlementGrantStatusSqlTest} 只渲染 SQL、不连库；这里验的是：
 * 状态值引用枚举的 {@code ${...}} 在 Spring 装配的 MyBatis-Plus 配置下能求值并真的命中行，
 * 以及状态列从 Integer 改成枚举、去掉 setter 之后，读库时仍被装配进来（装配不上时是静默的 null）。
 *
 * <p>放在 app-biz：领取在这个进程里执行。数据由用例自己造，结束时回滚。
 */
@SpringBootTest(classes = BizApplication.class)
@Transactional
class EntitlementGrantStatusFlowTest {

    private static final long MEMBER_ID = 920001L;

    @Autowired
    private GradeEntitlementGrantDao grantDao;

    @Test
    @DisplayName("🔴 待领取 → 已领取，同一份不能领两次，别人的领不走")
    void 领取() {
        GradeEntitlementGrant grant = insert(LocalDateTime.now().plusDays(3));
        assertEquals(EntitlementGrantStatusEnum.PENDING, statusOf(grant), "生成后读回的状态不对，或没装配上");

        assertEquals(0, grantDao.tryClaim(grant.getId(), MEMBER_ID + 1, LocalDateTime.now()), "别人的权益不该被领走");
        assertEquals(1, grantDao.tryClaim(grant.getId(), MEMBER_ID, LocalDateTime.now()), "待领取 → 已领取 的闸门没命中");
        assertEquals(0, grantDao.tryClaim(grant.getId(), MEMBER_ID, LocalDateTime.now()), "同一份不该被领两次");
        assertEquals(EntitlementGrantStatusEnum.CLAIMED, statusOf(grant));
    }

    @Test
    @DisplayName("到期未领 → 已过期，过期后领不了；已领取的不会被过期任务碰到")
    void 过期() {
        GradeEntitlementGrant expired = insert(LocalDateTime.now().minusMinutes(1));
        GradeEntitlementGrant claimed = insert(LocalDateTime.now().plusMinutes(1));
        grantDao.tryClaim(claimed.getId(), MEMBER_ID, LocalDateTime.now());

        grantDao.expirePending(LocalDateTime.now().plusMinutes(2), 100000);
        assertEquals(EntitlementGrantStatusEnum.EXPIRED, statusOf(expired));
        assertEquals(EntitlementGrantStatusEnum.CLAIMED, statusOf(claimed), "已领取的被过期任务改掉了");
        assertEquals(0, grantDao.tryClaim(expired.getId(), MEMBER_ID, LocalDateTime.now()), "过期的不该还能领");
    }

    private EntitlementGrantStatusEnum statusOf(GradeEntitlementGrant grant) {
        return grantDao.selectById(grant.getId()).getStatus();
    }

    private GradeEntitlementGrant insert(LocalDateTime expireTime) {
        long seq = System.nanoTime();
        GradeEntitlementGrant grant = new GradeEntitlementGrant();
        grant.setEntitlementId(seq);
        grant.setEntitlementCode("ZZTESTENT");
        grant.setMemberId(MEMBER_ID);
        grant.setPeriodKey("202611");
        grant.setGradeCode(1);
        grant.setGrantBizId("ZZTEST" + seq);
        grant.awaitClaim(expireTime);
        grantDao.insert(grant);
        return grant;
    }
}
