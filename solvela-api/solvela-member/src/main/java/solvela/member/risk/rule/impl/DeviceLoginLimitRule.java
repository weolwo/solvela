package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRuleConfig;
import solvela.member.risk.rule.TieredCountRule;

/**
 * 单设备一天的登录次数（含失败）。迁自 {@code DeviceGuard}（改造前是 dry-run）。
 *
 * <p>命中生效时还会请求把这台设备降进观察档 —— 处置写在 {@code params.observe}，
 * 而动作只有<b>生效</b>的命中才会被执行（陪跑期间只记录，见 {@link RiskAction}）。
 *
 * @Date 2026-10-15
 */
@Component
public class DeviceLoginLimitRule extends TieredCountRule {

    public DeviceLoginLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "DEVICE_LOGIN_LIMIT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.DEVICE_LOGIN;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getDeviceId();
    }

    @Override
    protected boolean countsThisRequest() {
        return true;
    }

    @Override
    protected String dimensionName() {
        return "本设备登录";
    }

    @Override
    protected RiskHit decorate(RiskHit hit, RiskRuleConfig config) {
        return config.params().path("observe").asBoolean(false) ? hit.withActions(RiskAction.OBSERVE_DEVICE) : hit;
    }
}
