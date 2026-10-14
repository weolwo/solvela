package solvela.member.risk;

import solvela.enums.MemberRiskDecisionEnum;

import java.util.Set;

/**
 * 一个阶段的<b>生效</b>结论。陪跑结论只落库、不返回 —— 调用方不该能拿它做任何决定。
 *
 * @param retryAfterSeconds 冷却 / 限流还要等多久（取给出这一档的命中里最长的那个）；其余情况为 0
 * @param actions           要执行的处置动作。只来自生效的命中（方案 §3.8）
 */
public record RiskDecision(MemberRiskDecisionEnum decision, long retryAfterSeconds, Set<RiskAction> actions) {

    public static final RiskDecision PASS = new RiskDecision(MemberRiskDecisionEnum.PASS, 0L, Set.of());

    public boolean is(MemberRiskDecisionEnum d) {
        return decision == d;
    }

    public boolean atLeast(MemberRiskDecisionEnum d) {
        return decision.atLeast(d);
    }

    /** 后置：要不要签凭票验码 */
    public boolean needsOtp() {
        return decision == MemberRiskDecisionEnum.OTP || decision == MemberRiskDecisionEnum.OTP_STRONG;
    }

    public boolean has(RiskAction action) {
        return actions.contains(action);
    }
}
