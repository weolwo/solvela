package solvela.member.risk.rule;

import solvela.enums.MemberRiskDecisionEnum;
import solvela.member.risk.RiskAction;

import java.util.Set;

/**
 * 一条规则的命中结果。
 *
 * @param decision          强规则 / 矩阵给出的处置；打分规则为 null
 * @param score             打分规则加的分；null = 用规则配置里的分值（灰名单这类分值跟着条目走的才填）
 * @param retryAfterSeconds 冷却 / 限流还要等多久；不适用时为 0
 * @param detail            给人看的命中依据，进 {@code t_member_risk_hit.detail}。🔴 不放身份明文
 * @param actions           请求执行的处置动作。<b>只是请求</b> —— 陪跑命中带的动作会被引擎丢掉（见 {@link RiskAction}）
 */
public record RiskHit(MemberRiskDecisionEnum decision, Integer score, long retryAfterSeconds, String detail,
                      Set<RiskAction> actions) {

    public static RiskHit decide(MemberRiskDecisionEnum decision, String detail) {
        return new RiskHit(decision, null, 0L, detail, Set.of());
    }

    public static RiskHit decide(MemberRiskDecisionEnum decision, long retryAfterSeconds, String detail) {
        return new RiskHit(decision, null, Math.max(0L, retryAfterSeconds), detail, Set.of());
    }

    public static RiskHit score(String detail) {
        return new RiskHit(null, null, 0L, detail, Set.of());
    }

    public static RiskHit score(int points, String detail) {
        return new RiskHit(null, points, 0L, detail, Set.of());
    }

    /** 挂上要请求的动作：{@code RiskHit.decide(LIMIT, 60, "…").withActions(OBSERVE_DEVICE)} */
    public RiskHit withActions(RiskAction... requested) {
        return new RiskHit(decision, score, retryAfterSeconds, detail, Set.of(requested));
    }
}
