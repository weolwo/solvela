package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控规则的类型。取值与 {@code t_member_risk_rule.rule_type} 一致。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskRuleTypeEnum implements BaseEnum {

    /** 命中即给出 {@code direct_decision} 那一档 */
    DIRECT(1, "强规则（固定处置）"),

    /** 命中给出的处置写在 params 里（分级阈值、矩阵格），由规则自己决定是哪一档 */
    TIERED(2, "强规则（分级 / 矩阵）"),

    /**
     * 命中加分。🔴 打分永远不会得出冷却 / 限流 / 拒绝 —— 那三档只来自强规则（方案 §3.4）。
     */
    SCORE(3, "打分规则"),
    ;

    private final Integer value;

    private final String desc;
}
