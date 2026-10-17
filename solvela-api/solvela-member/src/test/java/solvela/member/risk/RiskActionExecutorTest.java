package solvela.member.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.device.DeviceDispositionService;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.risk.security.CanaryTrap;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 风控动作的执行（{@link RiskActionExecutor}）：登录、注册共用的那一处。
 *
 * @Date 2026-10-18
 */
class RiskActionExecutorTest {

    private static final String DEVICE = "0123456789abcdef0123456789abcdef";

    private final DeviceDispositionService disposition = mock(DeviceDispositionService.class);

    private final MemberOperationLimitService operationLimit = mock(MemberOperationLimitService.class);

    private final CanaryTrap canaryTrap = mock(CanaryTrap.class);

    private final SuspiciousLoginNotifier notifier = mock(SuspiciousLoginNotifier.class);

    private final RiskActionExecutor executor = new RiskActionExecutor(disposition, operationLimit, canaryTrap, notifier);

    @Test
    @DisplayName("降观察档：理由按场景写（登录 / 注册各一句），没带设备就什么都不做")
    void observeDevice() {
        executor.execute(decision(RiskAction.OBSERVE_DEVICE), context(MemberRiskEventTypeEnum.LOGIN, 1L, DEVICE));
        executor.execute(decision(RiskAction.OBSERVE_DEVICE), context(MemberRiskEventTypeEnum.REGISTER, null, DEVICE));
        executor.execute(decision(RiskAction.OBSERVE_DEVICE), context(MemberRiskEventTypeEnum.LOGIN, 1L, null));

        verify(disposition).observe(DEVICE, "风控命中：量级异常");
        verify(disposition).observe(DEVICE, "风控命中：本设备建号过多");
        verifyNoInteractions(operationLimit, canaryTrap, notifier);
    }

    @Test
    @DisplayName("🔴 落冷却：查无此人时不写（那张表的 member_id 是 NOT NULL）；写失败不往外抛")
    void recordLimit() {
        executor.execute(decision(RiskAction.RECORD_LIMIT), context(MemberRiskEventTypeEnum.LOGIN, null, DEVICE));
        verify(operationLimit, never()).freeze(any(), any(), anyString(), any());

        doThrow(new IllegalStateException("库挂了")).when(operationLimit)
                .freeze(eq(7L), eq(MemberOperationTypeEnum.LOGIN), anyString(), any());
        executor.execute(decision(RiskAction.RECORD_LIMIT), context(MemberRiskEventTypeEnum.LOGIN, 7L, DEVICE));
        verify(operationLimit).freeze(eq(7L), eq(MemberOperationTypeEnum.LOGIN), anyString(), any());
    }

    @Test
    @DisplayName("蜜罐账号与通知主人：各自只在被请求时执行")
    void canaryAndNotify() {
        RiskContext ctx = context(MemberRiskEventTypeEnum.LOGIN, 7L, DEVICE);

        executor.execute(decision(RiskAction.CANARY_TRAP), ctx);
        verify(canaryTrap).spring(ctx);
        verify(notifier, never()).notifyOwner(anyLong(), any(), any());

        executor.execute(decision(RiskAction.NOTIFY_OWNER), ctx);
        verify(notifier).notifyOwner(eq(7L), eq("1.2.3.4"), any());
    }

    private static RiskDecision decision(RiskAction action) {
        return new RiskDecision(MemberRiskDecisionEnum.PASS, 60L, Set.of(action));
    }

    private static RiskContext context(MemberRiskEventTypeEnum eventType, Long memberId, String deviceId) {
        return new RiskContext(eventType, memberId, deviceId, "1.2.3.4");
    }
}
