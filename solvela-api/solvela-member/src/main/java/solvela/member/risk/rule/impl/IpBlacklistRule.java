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
 * IP / IP 段在黑名单里 → 拒绝。
 *
 * <p>对外的回答与限流<b>一模一样</b>（「操作过于频繁，请稍后再试」）：告诉对方「你被拉黑了」
 * 等于给他一个精确的调试反馈，他换个 IP 就继续。
 *
 * <h3>同时在黑、白名单里时黑名单优先</h3>
 * 白名单是「这个出口后面有很多真人，别按 IP 限他们的流」；黑名单是「这个 IP 确定在干坏事」。
 * 后者是人工判过的具体结论，不该被前者那种宽泛的豁免盖掉。
 *
 * @Date 2026-10-15
 */
@Component
public class IpBlacklistRule implements RiskRule {

    private final RiskListCache lists;

    public IpBlacklistRule(RiskListCache lists) {
        this.lists = lists;
    }

    @Override
    public String code() {
        return "IP_BLACKLIST";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public boolean alwaysEnforce() {
        return true;
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        ListMatch match = lists.matchIp(ctx.getClientIp());
        if (!match.black()) {
            return null;
        }
        // 条目 id 进命中依据：客服顺着它就能找到是谁、什么时候、为什么加的
        return RiskHit.decide(config.directDecision(), "IP 命中黑名单条目 #" + match.blackEntryId());
    }
}
