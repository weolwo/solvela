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
 * 单设备一小时的登录失败数。迁自 {@code DeviceGuard}（改造前是 dry-run）。
 *
 * <p>窗口比设备的其它计数短：连续失败是<b>撞库的即时信号</b>，用一天的窗口反应太慢；
 * 而误伤的代价也小 —— 真人一小时内输错十次密码本来就该被拦一下。
 *
 * <p>🔴 只读不加：计数由认证主干在真的失败时 +1。
 *
 * @Date 2026-10-15
 */
@Component
public class DeviceFailLimitRule extends TieredCountRule {

    public DeviceFailLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "DEVICE_FAIL_LIMIT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.DEVICE_FAIL;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getDeviceId();
    }

    @Override
    protected boolean countsThisRequest() {
        return false;
    }

    @Override
    protected String dimensionName() {
        return "本设备登录失败";
    }

    @Override
    protected RiskHit decorate(RiskHit hit, RiskRuleConfig config) {
        return config.params().path("observe").asBoolean(false) ? hit.withActions(RiskAction.OBSERVE_DEVICE) : hit;
    }
}
