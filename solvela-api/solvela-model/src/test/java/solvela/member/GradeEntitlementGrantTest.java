package solvela.member;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.EntitlementGrantStatusEnum;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeEntitlementGrantTest {

    private static final LocalDateTime EXPIRE = LocalDateTime.of(2026, 12, 31, 23, 59);

    @Test
    @DisplayName("生成为待领取，带领取截止时间；不能再定一次")
    void 生成待领取() {
        GradeEntitlementGrant grant = new GradeEntitlementGrant();
        grant.awaitClaim(EXPIRE);
        assertEquals(EntitlementGrantStatusEnum.PENDING, grant.getStatus());
        assertEquals(EXPIRE, grant.getExpireTime());
        assertThrows(IllegalStateException.class, () -> grant.awaitClaim(EXPIRE));
    }

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        GradeEntitlementGrant grant = new GradeEntitlementGrant();
        MetaObject meta = SystemMetaObject.forObject(grant);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", EntitlementGrantStatusEnum.CLAIMED);
        assertEquals(EntitlementGrantStatusEnum.CLAIMED, grant.getStatus());
    }
}
