package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.TieredCountRule;

/**
 * 单 IP 在一个窗口里<b>尝试不存在账号</b>的次数。迁自 {@code LoginIpGuard}。
 *
 * <h3>四个 IP 维度里信号最强的一个</h3>
 * 真人偶尔记错自己用哪个邮箱注册，一小时内试十个不存在的账号只可能是在批量撞库或枚举。
 * 它也补上了一个盲区：查不到人时没有 member_id，登录日志一行都写不了 ——
 * 这类尝试在风控流水出现之前完全看不见。
 *
 * <p>🔴 只读不加：计数由认证主干在查无此人时 +1。
 *
 * @Date 2026-10-15
 */
@Component
public class IpUnknownLimitRule extends TieredCountRule {

    public IpUnknownLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "IP_UNKNOWN_LIMIT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.IP_UNKNOWN;
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
        return "本 IP 尝试不存在的账号";
    }
}
