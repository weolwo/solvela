package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.config.ListMatch;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;

/**
 * IP / IP 段在灰名单里 → 加分。
 *
 * <p>灰名单是「有点可疑，但不足以拒绝」：加的分跟着<b>条目</b>走，不跟着规则走 ——
 * 同一批里有的 IP 值 20 分、有的值 50 分，而它们共用这一条规则。
 *
 * @Date 2026-10-15
 */
@Component
public class IpGreylistRule implements RiskRule {

    private final RiskListCache lists;

    public IpGreylistRule(RiskListCache lists) {
        this.lists = lists;
    }

    @Override
    public String code() {
        return "IP_GREYLIST";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        ListMatch match = lists.matchIp(ctx.getClientIp());
        if (!match.grey()) {
            return null;
        }
        return RiskHit.score(match.greyScore(), "IP 命中灰名单，加 " + match.greyScore() + " 分");
    }
}
