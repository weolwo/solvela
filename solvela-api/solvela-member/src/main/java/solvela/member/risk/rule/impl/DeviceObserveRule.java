package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.DeviceStatusEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.api.MemberLoginType;
import solvela.member.device.DeviceDispositionService;
import solvela.member.risk.RiskContext;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;

/**
 * 设备在观察档 → 登录要多验一道码。迁自 {@code MemberAuthService.checkDeviceChallenge}。
 *
 * <h3>为什么是「多验一道」而不是「直接拒」</h3>
 * 误伤的代价不对称：拦错一个正常用户，他不会来报障，只会不再打开；而多要一道验证码，
 * 正常用户只是多花十秒，刷子却要为<b>每一台设备</b>付出一条验证码的成本。
 *
 * <h3>🔴 邮箱验证码登录不再要第二道</h3>
 * 它本身就是一道验证码。再要一道的话，用户会在同一个邮箱里收到两封信，
 * 而第二封证明不了第一封证明不了的任何事情。
 *
 * <h3>为什么不被可信设备绕过</h3>
 * 观察档是设备闸撞线或人工降的档。「这台设备可信」不该洗白一个刚刚被判过的东西。
 *
 * @Date 2026-10-15
 */
@Component
public class DeviceObserveRule implements RiskRule {

    private final DeviceDispositionService dispositionService;

    public DeviceObserveRule(DeviceDispositionService dispositionService) {
        this.dispositionService = dispositionService;
    }

    @Override
    public String code() {
        return "DEVICE_OBSERVE";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.POST;
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getDeviceId() == null || ctx.getLoginType() == MemberLoginType.EMAIL_CODE) {
            return null;
        }
        if (dispositionService.currentStatus(ctx.getDeviceId()) != DeviceStatusEnum.OBSERVE) {
            return null;
        }
        return RiskHit.decide(config.directDecision(), "设备在观察档");
    }
}
