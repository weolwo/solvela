package solvela.admin.mapper;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.MemberVerifyStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.MemberVerify;
import solvela.member.verify.dao.MemberVerifyDao;
import solvela.member.verify.service.MemberVerifyService;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 实名认证审核在真库上走一遍，经 Service 入口。
 *
 * <p>以前是先读后 updateById：两个运营同时点，都会成功、后写的覆盖先写的（通过之后又被改成驳回，
 * 通过时间却留着）。这里验第二次审核一律被拒、已认证的记录状态与通过时间不被改动。
 * 目前没有代码会建核验记录（C 端提交还没做），用例自己造一条「认证中」，结束时回滚。
 */
@SpringBootTest
@Transactional
class MemberVerifyStatusFlowTest {

    private static final long MEMBER_ID = 990000000003L;

    @Autowired
    private MemberVerifyService service;
    @Autowired
    private MemberVerifyDao dao;

    @Test
    @DisplayName("🔴 通过之后再驳回、再通过都被拒；状态、通过时间不变，驳回原因被清掉")
    void 审核只放行一次() {
        MemberVerify verify = insertPending("上次照片模糊");

        service.approve(verify.getId());
        assertThrows(BusinessException.class, () -> service.reject(verify.getId(), "晚到的驳回"));
        assertThrows(BusinessException.class, () -> service.approve(verify.getId()));

        MemberVerify after = dao.selectById(verify.getId());
        assertAll(
                () -> assertEquals(MemberVerifyStatusEnum.VERIFIED, after.getVerifyStatus()),
                () -> assertNotNull(after.getVerifyTime(), "通过时间应由数据库写入"),
                () -> assertNull(after.getFailReason(), "通过时应清掉上次的驳回原因"));
    }

    @Test
    @DisplayName("驳回：落认证失败并留下原因，不写通过时间；之后不能再审核")
    void 驳回() {
        MemberVerify verify = insertPending(null);

        service.reject(verify.getId(), "  身份证号与姓名不符  ");
        assertThrows(BusinessException.class, () -> service.approve(verify.getId()));

        MemberVerify after = dao.selectById(verify.getId());
        assertAll(
                () -> assertEquals(MemberVerifyStatusEnum.FAILED, after.getVerifyStatus()),
                () -> assertEquals("身份证号与姓名不符", after.getFailReason()),
                () -> assertNull(after.getVerifyTime()));
    }

    private MemberVerify insertPending(String failReason) {
        MemberVerify verify = new MemberVerify();
        verify.setMemberId(MEMBER_ID);
        verify.setFailReason(failReason);
        // 生产代码里没有建核验记录的路径，状态没有 setter：走 MyBatis 读库时的同一条反射路径造一条「认证中」
        SystemMetaObject.forObject(verify).setValue("verifyStatus", MemberVerifyStatusEnum.PENDING);
        dao.insert(verify);
        return verify;
    }
}
