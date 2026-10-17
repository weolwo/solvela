package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.enums.MemberOperationTypeEnum;
import solvela.member.device.DeviceDispositionService;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.risk.security.CanaryTrap;

import java.time.LocalDateTime;

/**
 * 执行风控结论带的动作（{@link RiskAction}）。登录、注册共用这一处：一个动作只有一种做法。
 *
 * <p>拿到的都该执行 —— 陪跑规则请求的动作已经被 {@link RiskSession} 丢掉了。
 * 每个动作都只是<b>副作用</b>，不改变这次请求的结论；任何一个失败都不能让登录、注册跟着失败。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskActionExecutor {

    private final DeviceDispositionService dispositionService;

    private final MemberOperationLimitService operationLimitService;

    private final CanaryTrap canaryTrap;

    private final SuspiciousLoginNotifier suspiciousLoginNotifier;

    public void execute(RiskDecision decision, RiskContext ctx) {
        if (decision.has(RiskAction.OBSERVE_DEVICE) && ctx.getDeviceId() != null) {
            dispositionService.observe(ctx.getDeviceId(), observeReason(ctx));
        }
        if (decision.has(RiskAction.RECORD_LIMIT)) {
            recordLimit(decision, ctx);
        }
        if (decision.has(RiskAction.CANARY_TRAP)) {
            // 只有副作用：登录照常往下走，在密码比对那一步失败 —— 回答与真实账号输错密码逐字节相同
            canaryTrap.spring(ctx);
        }
        if (decision.has(RiskAction.NOTIFY_OWNER)) {
            /*
             * 「我们拦下了一次可疑登录」。触发面刻意很窄（只有 OTP_STRONG / DENY，
             * 见 RiskSession），而且按会员节流 —— 一次撞库会对同一个账号连试几十次。
             */
            suspiciousLoginNotifier.notifyOwner(ctx.memberId(), ctx.getClientIp(), ctx.getDeviceType());
        }
    }

    /** 设备进观察档的理由，写进设备那一行给人看 */
    private static String observeReason(RiskContext ctx) {
        return switch (ctx.getEventType()) {
            case LOGIN -> "风控命中：量级异常";
            case REGISTER -> "风控命中：本设备建号过多";
            default -> "风控命中：" + ctx.getEventType().getDesc();
        };
    }

    /**
     * 把长冷却落一行 {@code t_member_operation_limit}：后台看得见，客服解得开。
     *
     * <h3>🔴 查无此人时什么都不写</h3>
     * 那张表的 {@code member_id} 是 NOT NULL，而冷却是按<b>身份摘要</b>算的 ——
     * 不存在的账号照样会被冷却（那正是不泄露账号存在性的前提）。
     * 这一行只是给人看的副本，少一行不影响冷却本身。
     *
     * <h3>🔴 解除是两件事，这里只落其中一件</h3>
     * 真正拦人的是 Redis 里那个冷却键。客服在后台「解冻」只改这一行的话，
     * 用户照样登不进去 —— 所以后台的解冻按钮必须同时清 Redis（{@code PasswordCooldownStore.clear}）。
     */
    private void recordLimit(RiskDecision decision, RiskContext ctx) {
        if (ctx.memberId() == null) {
            return;
        }
        try {
            operationLimitService.freeze(ctx.memberId(), MemberOperationTypeEnum.LOGIN, "密码连续错误过多，风控冷却",
                    LocalDateTime.now().plusSeconds(Math.max(1L, decision.retryAfterSeconds())));
        } catch (RuntimeException e) {
            // 落不上只是后台少看见一行，绝不能让登录接口跟着失败
            log.error("【风控】记录操作限制失败, memberId: {}", ctx.memberId(), e);
        }
    }
}
