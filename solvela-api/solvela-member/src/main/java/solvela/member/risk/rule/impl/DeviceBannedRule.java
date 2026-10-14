package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.DeviceStatusEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.device.DeviceDispositionService;
import solvela.member.risk.RiskContext;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;

/**
 * 设备被人工封禁 → 拒绝。迁自 {@code DeviceGuard}。
 *
 * <h3>🔴 排在所有计数之前，而且 alwaysEnforce</h3>
 * 一台已经被人签字封掉的设备，不该再消耗任何计数配额，也不该因为「今天还没登够 30 次」而被放行。
 * 封禁是<b>有人看过之后做的决定</b>，陪跑、保命开关都不该把它放掉。
 *
 * @Date 2026-10-15
 */
@Component
public class DeviceBannedRule implements RiskRule {

    private final DeviceDispositionService dispositionService;

    public DeviceBannedRule(DeviceDispositionService dispositionService) {
        this.dispositionService = dispositionService;
    }

    @Override
    public String code() {
        return "DEVICE_BANNED";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public boolean alwaysEnforce() {
        return true;
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getDeviceId() == null) {
            return null;
        }
        if (dispositionService.currentStatus(ctx.getDeviceId()) != DeviceStatusEnum.BANNED) {
            return null;
        }
        return RiskHit.decide(config.directDecision(), "设备已被封禁");
    }
}
