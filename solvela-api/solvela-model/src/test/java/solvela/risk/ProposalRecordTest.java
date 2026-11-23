package solvela.risk;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.ProposalStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposalRecordTest {

    @Test
    @DisplayName("三种初始状态；风控拦截连同拦截分类一起写，其余两种不带分类")
    void 初始状态() {
        ProposalRecord review = new ProposalRecord();
        review.awaitFirstReview();
        assertEquals(ProposalStatusEnum.FIRST_REVIEW, review.getStatus());
        assertNull(review.getRiskCode());

        ProposalRecord execute = new ProposalRecord();
        execute.awaitExecution();
        assertEquals(ProposalStatusEnum.PENDING_EXECUTE, execute.getStatus());
        assertNull(execute.getRiskCode());

        ProposalRecord blocked = new ProposalRecord();
        blocked.blockByRisk("GLOBAL_BUDGET_LIMIT");
        assertEquals(ProposalStatusEnum.RISK_BLOCKED, blocked.getStatus());
        assertEquals("GLOBAL_BUDGET_LIMIT", blocked.getRiskCode());
    }

    @Test
    @DisplayName("初始状态只能定一次：读库出来的提案再调就是绕过闸门")
    void 只能定一次() {
        ProposalRecord proposal = new ProposalRecord();
        proposal.awaitExecution();
        assertThrows(IllegalStateException.class, proposal::awaitFirstReview);
        assertThrows(IllegalStateException.class, proposal::awaitExecution);
        assertThrows(IllegalStateException.class, () -> proposal.blockByRisk("X"));
    }

    /** 🔴 status / risk_code 没有 setter，读库时 MyBatis 必须还能把它们填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status 与 riskCode")
    void MyBatis仍能写入() {
        ProposalRecord proposal = new ProposalRecord();
        MetaObject meta = SystemMetaObject.forObject(proposal);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        assertTrue(meta.hasSetter("riskCode"), "MyBatis 认为 riskCode 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", ProposalStatusEnum.EXECUTING);
        meta.setValue("riskCode", "USER_FREQUENCY_LIMIT");
        assertEquals(ProposalStatusEnum.EXECUTING, proposal.getStatus());
        assertEquals("USER_FREQUENCY_LIMIT", proposal.getRiskCode());
    }
}
