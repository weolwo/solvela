package solvela.member;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MemberOperationLimitStatusEnum;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberOperationLimitTest {

    @Test
    @DisplayName("落为冻结中，冻结时间与到期时间一起定；不能再定一次")
    void 落冻结中() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 10, 0);
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.lockUntil(now, now.plusMinutes(30));
        assertEquals(MemberOperationLimitStatusEnum.LOCKED, limit.getStatus());
        assertEquals(now, limit.getLockTime());
        assertEquals(now.plusMinutes(30), limit.getExpireTime());
        assertThrows(IllegalStateException.class, () -> limit.lockUntil(now, now.plusHours(1)));
    }

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        MemberOperationLimit limit = new MemberOperationLimit();
        MetaObject meta = SystemMetaObject.forObject(limit);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", MemberOperationLimitStatusEnum.UNLOCKED);
        assertEquals(MemberOperationLimitStatusEnum.UNLOCKED, limit.getStatus());
    }
}
