package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控规则的档位。取值与 {@code t_member_risk_rule.mode}、{@code t_member_risk_hit.mode} 一致。
 *
 * <p>陪跑的意义：规则照常执行、命中照常落库，只是不影响结论 ——
 * 「把它切到生效会多打扰多少人」靠两份结论的差值来回答（方案 §3.6 / §8.2）。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskModeEnum implements BaseEnum {

    /** 不执行，连特征都不取 */
    OFF(0, "关闭"),

    /** 执行、记命中，不影响决策 */
    SHADOW(1, "陪跑"),

    /** 执行、记命中、参与决策 */
    ENFORCE(2, "生效"),
    ;

    private final Integer value;

    private final String desc;
}
