package solvela.draw;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.PrizePoolStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrizePoolConfigTest {

    @Test
    @DisplayName("开启 / 关闭两种初始状态只能定一次；没有 setter 时 MyBatis 仍能写入 status")
    void 初始状态与装配() {
        PrizePoolConfig open = new PrizePoolConfig();
        open.startOpen();
        assertEquals(PrizePoolStatusEnum.OPEN, open.getStatus());
        assertThrows(IllegalStateException.class, open::startClosed);

        PrizePoolConfig closed = new PrizePoolConfig();
        closed.startClosed();
        assertEquals(PrizePoolStatusEnum.CLOSED, closed.getStatus());
        assertThrows(IllegalStateException.class, closed::startOpen);

        assertTrue(SystemMetaObject.forObject(new PrizePoolConfig()).hasSetter("status"),
                "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
    }
}
