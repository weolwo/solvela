package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.TieredCountRule;

/**
 * 单 IP 在一个窗口里的登录失败数。迁自 {@code LoginIpGuard}。
 *
 * <p>🔴 <b>只读不加</b>：计数由认证主干在真的失败时 +1（密码错、验证码错、账号冻结、查无此人）。
 * 在这里 +1 的话，每一次正常登录也被算成一次失败。
 *
 * @Date 2026-10-15
 */
@Component
public class IpFailLimitRule extends TieredCountRule {

    public IpFailLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "IP_FAIL_LIMIT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.IP_FAIL;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getClientIp();
    }

    @Override
    protected boolean countsThisRequest() {
        return false;
    }

    @Override
    protected String dimensionName() {
        return "本 IP 登录失败";
    }
}
