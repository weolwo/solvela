package solvela.member;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberStatusFieldTest {

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null，登录会被当成非正常账号拒掉 */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入会员 status")
    void MyBatis仍能写入() {
        assertTrue(SystemMetaObject.forObject(new Member()).hasSetter("status"),
                "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
    }
}
