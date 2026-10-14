package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.TieredCountRule;

/**
 * 单 IP 在一个窗口里的登录请求总数（含成功）。迁自 {@code LoginIpGuard}。
 *
 * <p>它挡的是设备闸与账号冷却都数不到的那类脚本：<b>不带设备令牌</b>（设备维度对「没有设备」一律不判）、
 * <b>每个账号只试一次</b>（账号冷却要同一个身份连错几次才触发）。它唯一藏不住的是「从哪来」。
 *
 * @Date 2026-10-15
 */
@Component
public class IpAttemptLimitRule extends TieredCountRule {

    public IpAttemptLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "IP_ATTEMPT_LIMIT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.IP_ATTEMPT;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getClientIp();
    }

    /** 这一次请求本身就算一次 */
    @Override
    protected boolean countsThisRequest() {
        return true;
    }

    @Override
    protected String dimensionName() {
        return "本 IP 登录请求";
    }
}
