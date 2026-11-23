package solvela.prize;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.PrizeApproveStatusEnum;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeProposalStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrizeLogTest {

    @Test
    @DisplayName("落为等待执行、提案待提交；不能再定一次")
    void 落等待() {
        PrizeLog log = new PrizeLog();
        log.awaitDispatch();
        assertEquals(PrizeDispatchStatusEnum.WAITING, log.getStatus());
        assertEquals(PrizeProposalStatusEnum.PENDING, log.getProposalStatus());
        assertThrows(IllegalStateException.class, log::awaitDispatch);
    }

    @Test
    @DisplayName("审批初始状态：待审批 / 无需审批二选一，只能定一次")
    void 审批初始状态() {
        PrizeLog manual = new PrizeLog();
        manual.awaitApproval();
        assertEquals(PrizeApproveStatusEnum.PENDING, manual.getApproveStatus());
        assertThrows(IllegalStateException.class, manual::exemptFromApproval);

        PrizeLog auto = new PrizeLog();
        auto.exemptFromApproval();
        assertEquals(PrizeApproveStatusEnum.NOT_REQUIRED, auto.getApproveStatus());
        assertThrows(IllegalStateException.class, auto::awaitApproval);
    }

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入状态列与审批配套列")
    void MyBatis仍能写入状态() {
        PrizeLog log = new PrizeLog();
        MetaObject meta = SystemMetaObject.forObject(log);
        for (String field : new String[]{"status", "approveStatus", "approveBy", "approveTime", "proposalStatus"}) {
            assertTrue(meta.hasSetter(field), "MyBatis 认为 " + field + " 不可写，读库时会静默丢掉这一列");
        }
        meta.setValue("status", PrizeDispatchStatusEnum.FAIL);
        assertEquals(PrizeDispatchStatusEnum.FAIL, log.getStatus());
    }
}
