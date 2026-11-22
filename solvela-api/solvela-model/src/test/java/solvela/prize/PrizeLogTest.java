package solvela.prize;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.PrizeDispatchStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrizeLogTest {

    @Test
    @DisplayName("落为等待执行；不能再定一次")
    void 落等待() {
        PrizeLog log = new PrizeLog();
        log.awaitDispatch();
        assertEquals(PrizeDispatchStatusEnum.WAITING, log.getStatus());
        assertThrows(IllegalStateException.class, log::awaitDispatch);
    }

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        PrizeLog log = new PrizeLog();
        MetaObject meta = SystemMetaObject.forObject(log);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", PrizeDispatchStatusEnum.FAIL);
        assertEquals(PrizeDispatchStatusEnum.FAIL, log.getStatus());
    }
}
