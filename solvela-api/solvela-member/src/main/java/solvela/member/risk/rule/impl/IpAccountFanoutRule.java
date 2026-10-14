package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

/**
 * 单 IP 在一个窗口里尝试过多少个<b>不同账号</b>。撞库的特征正是一个 IP 挨个试一批号。
 *
 * <h3>🔴 数「尝试过的」，不是「登录成功的」</h3>
 * 撞库里成功的只是极少数。只数成功的话，一个试了一万个号、中了三个的脚本在这条规则上是「3」。
 *
 * <h3>🔴 不论账号存不存在都算，所以不泄露任何东西</h3>
 * 计的是身份摘要，而摘要在「查无此人」时照样算得出来 —— 两条路径在这条规则上的行为逐字相同。
 *
 * <p>出口 NAT 后面天然会有很多账号（校园网、公司），所以第一档是滑块而不是限流。
 *
 * @Date 2026-10-15
 */
@Component
public class IpAccountFanoutRule implements RiskRule {

    private final LoginCounters counters;

    public IpAccountFanoutRule(LoginCounters counters) {
        this.counters = counters;
    }

    @Override
    public String code() {
        return "IP_ACCOUNT_FANOUT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.tiers(params);
        RiskParams.duration(params, "window", Duration.ofHours(1));
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getClientIp() == null || ctx.getIdentityHashHex() == null) {
            return null;
        }
        // 只存摘要的前 16 位十六进制：这是一个「数不同的有几个」的集合，不需要能还原出是谁。
        // 60 位的碰撞概率在一个小时、一个 IP 的量级上可以忽略，而键长省一半
        String member = ctx.getIdentityHashHex().substring(0, Math.min(16, ctx.getIdentityHashHex().length()));
        long distinct = counters.addToSet(LoginCounters.Counter.IP_IDENTITY, ctx.getClientIp(), member);
        List<RiskParams.Tier> tiers = RiskParams.tiers(config.params());
        RiskParams.Tier tier = RiskParams.hit(tiers, distinct);
        if (tier == null) {
            return null;
        }
        return RiskHit.decide(tier.decision(),
                counters.retryAfterSeconds(LoginCounters.Counter.IP_IDENTITY, ctx.getClientIp()),
                "本 IP 本窗口尝试了 " + distinct + " 个不同账号（超过 " + tier.over() + "）");
    }
}
