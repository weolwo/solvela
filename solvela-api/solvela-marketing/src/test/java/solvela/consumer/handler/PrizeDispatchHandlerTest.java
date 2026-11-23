package solvela.consumer.handler;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.consumer.strategy.PrizeStrategyFactory;
import solvela.dispatch.DispatchOutcome;
import solvela.exception.BusinessException;
import solvela.enums.PrizeApproveStatusEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;
import solvela.prize.prizelog.dao.PrizeLogDao;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 派发入口 {@code doDispatch} 的审批检查。
 *
 * <p>它是 public 的，对账重投、审批通过、脚本都能调。对账重投曾经不看审批状态，
 * 把待审批、已驳回的奖一起发了出去 —— 这里盯的是「入口本身就拒绝」，不靠每个调用方各自记得筛。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrizeDispatchHandlerTest {

    @Mock
    private PrizeStrategyFactory strategyFactory;
    @Mock
    private PrizeLogDao prizeLogDao;
    @Mock
    private IPrizeHandler prizeHandler;

    @InjectMocks
    private PrizeDispatchHandler handler;

    @ParameterizedTest
    @EnumSource(value = PrizeApproveStatusEnum.class, names = {"PENDING", "REJECTED"})
    @DisplayName("🔴 待审批、已驳回：直接拒绝，策略一次都不调、记录一行都不写")
    void 未批准不派发(PrizeApproveStatusEnum approve) {
        assertThrows(IllegalStateException.class, () -> handler.doDispatch(prizeLog(approve)));

        verifyNoInteractions(strategyFactory, prizeLogDao);
    }

    @ParameterizedTest
    @EnumSource(value = PrizeApproveStatusEnum.class, names = {"NOT_REQUIRED", "PASSED"})
    @DisplayName("无需审批、已批准：照常走策略")
    void 允许的照常派发(PrizeApproveStatusEnum approve) {
        when(strategyFactory.getHandler(PrizeTypeEnum.SCORE)).thenReturn(prizeHandler);
        when(prizeHandler.dispatch(any())).thenReturn(DispatchOutcome.success(9L));

        handler.doDispatch(prizeLog(approve));

        verify(prizeHandler).dispatch(any());
        verify(prizeLogDao).markProposalAccepted(1L, 9L);
        verify(prizeLogDao, never()).markDispatchFailed(any(), any());
        verify(prizeLogDao, never()).markProposalRejected(any(), any());
    }

    @Test
    @DisplayName("🔴 审批通过：闸门之后重读记录再派发 —— 拿着读出来的那份「待审批」去派发会被入口检查拒掉")
    void 审批通过重读后派发() {
        when(prizeLogDao.selectById(1L))
                .thenReturn(prizeLog(PrizeApproveStatusEnum.PENDING))
                .thenReturn(prizeLog(PrizeApproveStatusEnum.PASSED));
        when(prizeLogDao.markApproved(1L, "运营A")).thenReturn(1);
        when(strategyFactory.getHandler(PrizeTypeEnum.SCORE)).thenReturn(prizeHandler);
        when(prizeHandler.dispatch(any())).thenReturn(DispatchOutcome.success(9L));

        handler.approveDispatch(1L, "运营A");

        verify(prizeHandler).dispatch(any());
    }

    @Test
    @DisplayName("审批闸门抢不到：抛「已被处理」，不派发")
    void 审批抢不到不派发() {
        when(prizeLogDao.selectById(1L)).thenReturn(prizeLog(PrizeApproveStatusEnum.PENDING));
        when(prizeLogDao.markApproved(1L, "运营A")).thenReturn(0);

        assertThrows(BusinessException.class, () -> handler.approveDispatch(1L, "运营A"));

        verifyNoInteractions(strategyFactory);
    }

    private PrizeLog prizeLog(PrizeApproveStatusEnum approve) {
        PrizeLog log = new PrizeLog();
        log.setId(1L);
        log.setPrizeType(PrizeTypeEnum.SCORE);
        log.setPrizeValue("10");
        log.awaitDispatch();
        // 审批状态没有 setter：走 MyBatis 读库时的同一条反射路径，造出「库里读出来就是这个审批状态」的对象
        SystemMetaObject.forObject(log).setValue("approveStatus", approve);
        return log;
    }
}
