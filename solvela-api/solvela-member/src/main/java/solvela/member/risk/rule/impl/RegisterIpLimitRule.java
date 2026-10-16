package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.RegisterTieredCountRule;

/**
 * 单 IP 在一个窗口里注册了几个号。迁自 {@code MemberRegisterService.consumeAttempt}
 * （原来是硬编码的 10 个 / 小时）。
 *
 * <h3>它和设备那一维是两件事，都要过</h3>
 * IP 走代理池就换；而设备号得先过一次签发限频才拿得到 —— 后者贵得多。
 * 两维都留着，是因为它们各自挡住的是不同的人：
 * <ul>
 *   <li>一个人用一台机器批量建号 → 设备维度先拦住；</li>
 *   <li>一堆代理 IP、每个 IP 建一个号 → 设备维度数不到（每次都是新令牌），IP 维度也数不到 ——
 *       🔴 这一类只有「设备签发限频」能挡（每 IP 一天最多签发几个设备），
 *       那一道在网关的 {@code DeviceService} 里，不在风控引擎里。</li>
 * </ul>
 *
 * <h3>🔴 阈值刻意给得宽</h3>
 * 公司出口、校园网、运营商 NAT 背后是成百上千个真人，而注册是他们的第一次接触 ——
 * 在这里误伤一个人，他不会来投诉，他只是走了。
 *
 * @Date 2026-10-16
 */
@Component
public class RegisterIpLimitRule extends RegisterTieredCountRule {

    public RegisterIpLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "REG_IP_LIMIT";
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.REGISTER_IP;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getClientIp();
    }

    /** 这一次注册请求本身就算一次 */
    @Override
    protected boolean countsThisRequest() {
        return true;
    }

    @Override
    protected String dimensionName() {
        return "本 IP 注册";
    }
}
