package solvela.lottery;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.IssueStatusEnum;
import solvela.enums.LotteryConfigStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 期号与玩法的初始状态方法，以及去掉 setter 之后 MyBatis 仍能装配 */
class LotteryIssueTest {

    @Test
    @DisplayName("期号落为待开奖、玩法落为下线；都只能定一次")
    void 初始状态() {
        LotteryIssue issue = new LotteryIssue();
        issue.awaitDraw();
        assertEquals(IssueStatusEnum.WAIT, issue.getStatus());
        assertThrows(IllegalStateException.class, issue::awaitDraw);

        LotteryConfig config = new LotteryConfig();
        config.startOffline();
        assertEquals(LotteryConfigStatusEnum.OFFLINE, config.getStatus());
        assertThrows(IllegalStateException.class, config::startOffline);
    }

    /** 🔴 这些列没有 setter，读库时 MyBatis 必须还能把它们填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入期号状态、开奖号码、结算时间与玩法状态")
    void MyBatis仍能写入() {
        MetaObject issue = SystemMetaObject.forObject(new LotteryIssue());
        for (String field : new String[]{"status", "winningNumber", "settleTime"}) {
            assertTrue(issue.hasSetter(field), "MyBatis 认为 LotteryIssue." + field + " 不可写，读库时会静默丢掉这一列");
        }
        assertTrue(SystemMetaObject.forObject(new LotteryConfig()).hasSetter("status"),
                "MyBatis 认为 LotteryConfig.status 不可写");
    }
}
