package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import solvela.enums.MemberStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.dao.MemberDao;
import solvela.member.service.MemberService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 后台冻结 / 解冻在真库上走一遍，经 Service 入口；已注销的会员两条闸门都影响 0 行。
 * 会员用 JDBC 直接造（与 {@code FreezeRevokesSessionTest} 同一做法），结束时删掉。
 */
@SpringBootTest
class MemberStatusFlowTest {

    private static final long MEMBER_ID = 9999999997L;

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberDao memberDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("INSERT INTO t_member (member_id, member_name, nickname, status) VALUES (?, ?, ?, ?)",
                MEMBER_ID, "ZzMemberStatus", "状态机验收", MemberStatusEnum.NORMAL.getValue());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", MEMBER_ID);
    }

    @Test
    @DisplayName("冻结 → 重复冻结不报错 → 解冻；状态读回一致")
    void 冻结解冻() {
        memberService.updateStatus(MEMBER_ID, MemberStatusEnum.FROZEN, "运营A");
        memberService.updateStatus(MEMBER_ID, MemberStatusEnum.FROZEN, "运营A");
        assertEquals(MemberStatusEnum.FROZEN, statusOf());
        assertEquals(0, memberDao.markFrozen(MEMBER_ID, "运营B"), "已冻结的又被冻结了一次");

        memberService.updateStatus(MEMBER_ID, MemberStatusEnum.NORMAL, "运营A");
        assertEquals(MemberStatusEnum.NORMAL, statusOf());
    }

    @Test
    @DisplayName("🔴 已注销的会员：两条闸门都影响 0 行，Service 拒绝，状态不变")
    void 已注销不能改() {
        jdbcTemplate.update("UPDATE t_member SET status = ? WHERE member_id = ?", MemberStatusEnum.CANCELLED.getValue(), MEMBER_ID);

        assertEquals(0, memberDao.markFrozen(MEMBER_ID, "运营A"));
        assertEquals(0, memberDao.markNormal(MEMBER_ID, "运营A"));
        assertThrows(BusinessException.class, () -> memberService.updateStatus(MEMBER_ID, MemberStatusEnum.NORMAL, "运营A"));
        assertEquals(MemberStatusEnum.CANCELLED, statusOf());
    }

    private MemberStatusEnum statusOf() {
        return memberDao.selectById(MEMBER_ID).getStatus();
    }
}
