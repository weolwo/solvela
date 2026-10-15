package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskIpFamiliarity;
import solvela.member.risk.feature.Familiarity;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

/**
 * 前置矩阵：<b>默认要滑块，熟悉的网络放宽</b>。
 *
 * <h3>🔴 只能放宽，不能收紧</h3>
 * 本规则在验密码<b>之前</b>跑，而它用到了账号信息（这个人的常用网段）。
 * 所以它的结论对「查无此人」必须是那个<b>最严的默认值</b>（要滑块）——
 * 只有账号存在、而且网段对得上时才放宽。于是攻击者无论打存在还是不存在的账号，
 * 看到的都是同一个「要滑块」，这个接口不会变成账号枚举器（方案 §3.1）。
 *
 * <h3>🔴 只认严格同网段，不认「同城市 + 同运营商」</h3>
 * 宽松口径是给后置用的（那时密码已经验对了）。在这里用的话，
 * 一个和受害者在同一个城市、用同一家运营商的攻击者，对一部分账号就能跳过滑块。
 *
 * <h3>上线时它没有效果，这是对的</h3>
 * 全局滑块默认开着，而那条规则是 {@code alwaysEnforce}、给出的是同一档处置 ——
 * 所以本规则放宽谁都不起作用。它的命中照样落库，于是关掉全局滑块之前，
 * 就能先从流水里看出「有多少人会因此免掉滑块」（方案 §9）。
 *
 * @Date 2026-10-15
 */
@Component
public class CaptchaMatrixRule implements RiskRule {

    private final Familiarity familiarity;

    public CaptchaMatrixRule(Familiarity familiarity) {
        this.familiarity = familiarity;
    }

    @Override
    public String code() {
        return "CAPTCHA_MATRIX";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.positiveInt(params, "v4Prefix", 24);
        RiskParams.positiveInt(params, "v6Prefix", 64);
        RiskParams.positiveInt(params, "recentIps", 10);
        RiskParams.positiveInt(params, "lookbackDays", 90);
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        RiskIpFamiliarity ip = familiarity.of(ctx, config.params());
        if (ctx.memberExists() && ip.strict()) {
            // 放宽：不命中，也就不给出「要滑块」那一档
            return null;
        }
        return RiskHit.decide(MemberRiskDecisionEnum.CAPTCHA,
                ctx.memberExists() ? "IP 不在常用网段" : "默认要滑块");
    }
}
