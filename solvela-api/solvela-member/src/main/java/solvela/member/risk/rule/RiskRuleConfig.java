package solvela.member.risk.rule;

import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import tools.jackson.databind.JsonNode;

/**
 * 一条规则此刻的配置（从 {@code t_member_risk_rule} 加载、已解析、已校验）。不可变。
 *
 * @param params 解析后的参数；表里为 NULL 时是一个空对象，规则不用判 null
 */
public record RiskRuleConfig(String code, String name, MemberRiskEventTypeEnum eventType,
                             MemberRiskPhaseEnum phase, MemberRiskRuleTypeEnum ruleType,
                             MemberRiskDecisionEnum directDecision, int score, String group,
                             MemberRiskModeEnum mode, boolean bypassByTrust, JsonNode params) {
}
