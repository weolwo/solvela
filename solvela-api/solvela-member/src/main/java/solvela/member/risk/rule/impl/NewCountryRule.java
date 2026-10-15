package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.Familiarity;
import solvela.member.risk.feature.IpIntel;
import solvela.member.risk.feature.LoginProfile;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

/**
 * 这个国家他近 90 天没来过。
 *
 * <h3>🔴 冷启动不判</h3>
 * 成功登录少于 {@code minSuccess} 次时直接跳过：没有画像的时候「从没见过的国家」是<b>必然的</b>，
 * 判了等于给每个新用户都加一笔分。分不清的时候不判 —— 与新设备提醒「分不清就不发」同一个取向。
 *
 * <p>地理组里的一条（{@code rule_group = GEO}）：换国家必然换城市，
 * 同组只取最高分，免得一个真出差的人仅凭「人在外地」被算两遍。
 *
 * @Date 2026-10-15
 */
@Component
public class NewCountryRule implements RiskRule {

    private static final int DEFAULT_MIN_SUCCESS = 3;

    private final Familiarity familiarity;

    public NewCountryRule(Familiarity familiarity) {
        this.familiarity = familiarity;
    }

    @Override
    public String code() {
        return "NEW_COUNTRY";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.POST;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.positiveInt(params, "minSuccess", DEFAULT_MIN_SUCCESS);
        RiskParams.positiveInt(params, "lookbackDays", 90);
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        IpIntel.Location location = familiarity.location(ctx, config.params());
        if (location.unknown() || location.intranet()) {
            return null;
        }
        LoginProfile profile = familiarity.profile(ctx, config.params());
        if (profile.successCount() < RiskParams.positiveInt(config.params(), "minSuccess", DEFAULT_MIN_SUCCESS)) {
            return null;
        }
        return profile.knownCountry(location.country())
                ? null
                : RiskHit.score("近期没在 " + location.country() + " 登录过");
    }
}
