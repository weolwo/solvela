package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.Familiarity;
import solvela.member.risk.feature.IpIntel;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

/**
 * 来源 IP 是机房 —— 真人不会从阿里云登录。
 *
 * <h3>⚠️ 关键字级，覆盖不全</h3>
 * 它只认得 {@code params.keywords} 里列出的那几家。换一个小厂商、或者走住宅代理就绕开了。
 * 所以分值不该高到能单独决定什么，而真正可靠的识别要接商业 IP 情报（P3）。
 *
 * <p>内网 IP 不判：那只可能是内部工具或压测。
 *
 * @Date 2026-10-15
 */
@Component
public class IpDatacenterRule implements RiskRule {

    private final IpIntel ipIntel;

    private final Familiarity familiarity;

    public IpDatacenterRule(IpIntel ipIntel, Familiarity familiarity) {
        this.ipIntel = ipIntel;
        this.familiarity = familiarity;
    }

    @Override
    public String code() {
        return "IP_DATACENTER";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        if (IpIntel.keywords(params).isEmpty()) {
            throw new IllegalArgumentException("keywords 不能为空，否则这条规则永远不会命中");
        }
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        IpIntel.Location location = familiarity.location(ctx, config.params());
        if (location.unknown() || location.intranet()) {
            return null;
        }
        return ipIntel.datacenter(location, config.params())
                ? RiskHit.score("来源是机房 IP：" + location.isp())
                : null;
    }
}
