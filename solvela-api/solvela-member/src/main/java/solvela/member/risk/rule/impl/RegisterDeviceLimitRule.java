package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.RegisterTieredCountRule;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRuleConfig;

/**
 * 单设备在一个窗口里注册了几个号。迁自 {@code DeviceGuard.checkRegister}
 * （原来是硬编码的 2 个 / 天）。
 *
 * <h3>这条比 IP 那一维硬得多</h3>
 * 一个真人一天注册两个号已经很少见（家人共用一台平板），而批量建号必然在同一台设备上 ——
 * 换设备号要先过一次「每 IP 每天最多签发几个设备」，那是贵的。
 *
 * <h3>⚠️ 它的计数由本规则 +1，而不是像以前那样由注册主干调 {@code DeviceGuard}</h3>
 * 「超没超」和「+1」在同一个地方，少一处漂移的机会。
 * 老的 {@code DeviceGuard.checkRegister} 随这条规则一起删掉 —— 不留两个真相源。
 *
 * @Date 2026-10-16
 */
@Component
public class RegisterDeviceLimitRule extends RegisterTieredCountRule {

    public RegisterDeviceLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "REG_DEVICE_LIMIT";
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.REGISTER_DEVICE;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getDeviceId();
    }

    /** 这一次注册请求本身就算一次 */
    @Override
    protected boolean countsThisRequest() {
        return true;
    }

    @Override
    protected String dimensionName() {
        return "本设备注册";
    }

    /**
     * 命中生效时把这台设备降进观察档 —— 它下一次<b>登录</b>要多验一道码。
     *
     * <p>这正是改造前 {@code DeviceGuard.verdict} 里那行 {@code dispositionService.observe}，
     * 而它当时因为 dry-run 从没真的执行过。现在它由 {@code params.observe} 控制，
     * 并且只有<b>生效</b>的命中才会被执行（陪跑期间只记录，见 {@code RiskAction}）。
     */
    @Override
    protected RiskHit decorate(RiskHit hit, RiskRuleConfig config) {
        return config.params().path("observe").asBoolean(false) ? hit.withActions(RiskAction.OBSERVE_DEVICE) : hit;
    }
}
