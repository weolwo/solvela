package solvela.prize.prizelog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solvela.enums.PrizeApproveStatusEnum;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeProposalStatusEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.member.service.MemberService;
import solvela.prize.PrizeLog;
import solvela.prize.prizelog.dao.PrizeLogDao;
import solvela.prize.prizelog.domain.command.PrizeLogAddCommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台人工补发。
 *
 * <p>🔴 补发必须落「待审批」：它是一次真实发放的起点，真正发出去要走审批通过。
 * 审批状态没有 setter，{@code SolvelaBeanUtil.copy} 拷不进去；服务端漏了定状态那一句，
 * 库里会按列默认值落成 0-无需审批，对账任务十分钟后就会把它发出去 —— 不报任何错。
 */
@ExtendWith(MockitoExtension.class)
class PrizeLogServiceTest {

    @Mock
    private PrizeLogDao prizeLogDao;
    @Mock
    private MemberService memberService;

    @InjectMocks
    private PrizeLogService service;

    @Test
    @DisplayName("🔴 补发落待审批 + 等待执行 + 提案待提交，由服务端定")
    void 补发落待审批() {
        when(memberService.requireMemberName(940001L)).thenReturn("zz-test");
        PrizeLogAddCommand command = new PrizeLogAddCommand();
        command.setMemberId(940001L);
        command.setPrizeCode("ZZPRIZE");
        command.setPrizeType(PrizeTypeEnum.COUPON);
        command.setPrizeValue("1");

        service.add(command);

        ArgumentCaptor<PrizeLog> captor = ArgumentCaptor.forClass(PrizeLog.class);
        verify(prizeLogDao).insert(captor.capture());
        PrizeLog saved = captor.getValue();
        assertEquals(PrizeApproveStatusEnum.PENDING, saved.getApproveStatus());
        assertEquals(PrizeDispatchStatusEnum.WAITING, saved.getStatus());
        assertEquals(PrizeProposalStatusEnum.PENDING, saved.getProposalStatus());
        assertEquals("zz-test", saved.getMemberName());
    }
}
