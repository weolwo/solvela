package solvela.member;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberVerifyTest {

    /** 🔴 认证状态与通过时间没有 setter，读库时 MyBatis 必须还能把它们填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入认证状态与通过时间")
    void MyBatis仍能写入() {
        MetaObject meta = SystemMetaObject.forObject(new MemberVerify());
        assertTrue(meta.hasSetter("verifyStatus"), "MyBatis 认为 verifyStatus 不可写，读库时会静默丢掉这一列");
        assertTrue(meta.hasSetter("verifyTime"), "MyBatis 认为 verifyTime 不可写，读库时会静默丢掉这一列");
    }
}
