package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.ResetTieredCountRule;

/**
 * 单 IP 在一个窗口里提交了几次重置密码。
 *
 * <h3>🔴 它抓的是「拿着泄露的身份库挨个试重置码」</h3>
 * 这件事以前<b>一个计数器都数不到</b>：发码那边按目标限额（一个邮箱一天 10 条），
 * 而挨个试的人每个目标只试一次。
 *
 * <p>一个真人一小时提交几次重置？手滑输错码算在内，也就三五次。
 * 阈值给 20 是因为<b>这一维误伤的代价最高</b>：被挡下的人已经进不去账号了。
 *
 * @Date 2026-10-16
 */
@Component
public class ResetIpLimitRule extends ResetTieredCountRule {

    public ResetIpLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "RESET_IP_LIMIT";
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.RESET_IP;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getClientIp();
    }

    /** 这一次提交本身就算一次 */
    @Override
    protected boolean countsThisRequest() {
        return true;
    }

    @Override
    protected String dimensionName() {
        return "本 IP 重置密码";
    }
}
