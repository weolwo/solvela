package solvela.member;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.SecurityListStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityListTest {

    @Test
    @DisplayName("人工新增落为有效；不能再定一次；没有 setter 时 MyBatis 仍能写入 status")
    void 初始状态与装配() {
        SecurityList row = new SecurityList();
        row.startValid();
        assertEquals(SecurityListStatusEnum.VALID, row.getStatus());
        assertThrows(IllegalStateException.class, row::startValid);
        assertTrue(SystemMetaObject.forObject(new SecurityList()).hasSetter("status"),
                "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
    }
}
