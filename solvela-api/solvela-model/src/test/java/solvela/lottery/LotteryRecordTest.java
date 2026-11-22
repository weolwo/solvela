package solvela.lottery;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.LotteryDispatchStatusEnum;
import solvela.enums.TicketStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LotteryRecordTest {

    @Test
    @DisplayName("出票：未开奖、待派发；不能再出一次")
    void 出票() {
        LotteryRecord record = new LotteryRecord();
        record.openTicket();
        assertEquals(TicketStatusEnum.WAIT, record.getWinStatus());
        assertEquals(LotteryDispatchStatusEnum.WAITING, record.getDispatchStatus());
        assertThrows(IllegalStateException.class, record::openTicket);
    }

    /** 🔴 两列状态都没有 setter，读库时 MyBatis 必须还能把它们填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入两列状态")
    void MyBatis仍能写入状态() {
        LotteryRecord record = new LotteryRecord();
        MetaObject meta = SystemMetaObject.forObject(record);
        assertTrue(meta.hasSetter("winStatus") && meta.hasSetter("dispatchStatus"),
                "MyBatis 认为状态列不可写，读库时会静默丢掉");
        meta.setValue("winStatus", TicketStatusEnum.SUCCESS_MATCH);
        meta.setValue("dispatchStatus", LotteryDispatchStatusEnum.FAILED);
        assertEquals(TicketStatusEnum.SUCCESS_MATCH, record.getWinStatus());
        assertEquals(LotteryDispatchStatusEnum.FAILED, record.getDispatchStatus());
    }
}
