package solvela.member.risk;

import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一本账：一次尝试在各阶段攒下的分与结论。{@link RiskSession} 同时记两本（方案 §8.2）：
 * <ul>
 *   <li>{@link #effective 生效账}：只算 {@code ENFORCE} 的命中 —— 用户实际受到的处理；</li>
 *   <li>{@link #shadow 陪跑账}：{@code SHADOW + ENFORCE} 全算 —— 假如所有规则都打开，这个人会受到的处理。</li>
 * </ul>
 * 两本账的算法完全相同，只差「哪些命中算数」。{@code error} 的命中两本都不算：它的语义就是「这条规则这次没判出来」。
 *
 * @Date 2026-10-15
 */
final class RiskLedger {

    private final boolean onlyEnforce;

    private final MemberRiskProperties thresholds;

    private final Map<MemberRiskPhaseEnum, Integer> scores = new EnumMap<>(MemberRiskPhaseEnum.class);

    private MemberRiskDecisionEnum decision = MemberRiskDecisionEnum.PASS;

    private RiskLedger(boolean onlyEnforce, MemberRiskProperties thresholds) {
        this.onlyEnforce = onlyEnforce;
        this.thresholds = thresholds;
    }

    static RiskLedger effective(MemberRiskProperties thresholds) {
        return new RiskLedger(true, thresholds);
    }

    static RiskLedger shadow(MemberRiskProperties thresholds) {
        return new RiskLedger(false, thresholds);
    }

    /** 收下一个阶段的命中，结论只升不降 */
    void settle(MemberRiskPhaseEnum phase, List<RiskSession.Evaluated> phaseHits) {
        scores.put(phase, aggregateScore(phaseHits));
        decision = MemberRiskDecisionEnum.max(decision, phaseDecision(phase, phaseHits));
    }

    MemberRiskDecisionEnum decision() {
        return decision;
    }

    int score(MemberRiskPhaseEnum phase) {
        return scores.getOrDefault(phase, 0);
    }

    int totalScore() {
        return scores.values().stream().mapToInt(Integer::intValue).sum();
    }

    private boolean counts(RiskSession.Evaluated hit) {
        return !hit.error() && (!onlyEnforce || hit.mode() == MemberRiskModeEnum.ENFORCE);
    }

    /** 本阶段的结论 = 强规则里最严的那个，与打分换算出来的那一档，取更严的 */
    private MemberRiskDecisionEnum phaseDecision(MemberRiskPhaseEnum phase, List<RiskSession.Evaluated> phaseHits) {
        MemberRiskDecisionEnum strongest = phaseHits.stream()
                .filter(hit -> counts(hit) && hit.decision() != null)
                .map(RiskSession.Evaluated::decision)
                .reduce(null, MemberRiskDecisionEnum::max);
        return MemberRiskDecisionEnum.max(strongest, fromScore(phase));
    }

    /**
     * 打分换算成处置。<b>只会得出滑块 / OTP / 换通道 OTP</b> ——
     * 冷却、限流、拒绝那三档只来自强规则（方案 §3.4）：打分规则都是「像不像」的统计信号，没有一条是证据。
     */
    private MemberRiskDecisionEnum fromScore(MemberRiskPhaseEnum phase) {
        int pre = score(MemberRiskPhaseEnum.PRE);
        if (phase == MemberRiskPhaseEnum.PRE) {
            return reached(pre, thresholds.getPreCaptchaScore()) ? MemberRiskDecisionEnum.CAPTCHA : null;
        }
        // 🔴 前置的分带进后置：机房 IP（前置 +30）+ 新设备（后置 +30）在前置都不够一档，
        //    叠起来才够到 OTP —— 这正是打分比硬门槛强的地方
        int total = pre + score(MemberRiskPhaseEnum.POST);
        if (reached(total, thresholds.getStrongOtpScore())) {
            return MemberRiskDecisionEnum.OTP_STRONG;
        }
        return reached(total, thresholds.getOtpScore()) ? MemberRiskDecisionEnum.OTP : null;
    }

    /** 阈值配成 0 或负数时当作「这一档关掉了」，而不是「所有人都命中」 */
    private static boolean reached(int score, int threshold) {
        return threshold > 0 && score >= threshold;
    }

    /**
     * 分数汇总：<b>同组取最大，组间相加</b>。
     *
     * <p>换国家必然换城市 —— 两条一起加，一个真出差的人仅凭「人在外地」这一件事就被算了两遍。
     * 组外的规则（新设备、机房 IP、失败爆发）是互相独立的证据，照常相加（方案 §3.3）。
     *
     * <p>⚠️ 分组在<b>阶段内</b>生效。跨阶段的组是设计错误：前置的分已经先汇总过一次了，
     * 到后置再和同组的后置命中比最大值，得到的不是任何人想要的数。首批规则里 GEO 组全在后置。
     */
    private int aggregateScore(List<RiskSession.Evaluated> phaseHits) {
        int ungrouped = 0;
        Map<String, Integer> groupMax = new HashMap<>();
        for (RiskSession.Evaluated hit : phaseHits) {
            if (!counts(hit) || hit.score() <= 0) {
                continue;
            }
            if (hit.group() == null) {
                ungrouped += hit.score();
            } else {
                groupMax.merge(hit.group(), hit.score(), Math::max);
            }
        }
        return ungrouped + groupMax.values().stream().mapToInt(Integer::intValue).sum();
    }
}
