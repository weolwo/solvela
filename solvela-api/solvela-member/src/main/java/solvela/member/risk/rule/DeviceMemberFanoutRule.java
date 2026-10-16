package solvela.member.risk.rule;

import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 「<b>这一台设备在替几个不同的人</b>做某件敏感操作」的公共实现。
 *
 * <h3>它和登录侧的碰号数（{@code DEVICE_FANOUT}）数的不是一件事</h3>
 * 那一条只在<b>登录</b>时 add，于是买号的人完全可以在每个号上只登录一次 ——
 * 登录侧一次都不会超，而他接着在同一台设备上挨个把它们搬空、挨个换掉联系方式。
 * 所以每个敏感场景要有<b>自己的</b>碰号数，键也各自独立。
 *
 * <h3>🔴 先记后判</h3>
 * 超了也要把关联关系记下来 —— 「超了就不记」会让这台设备的真实量级被抹平，
 * 而那正是攻击时最该看清的数。判据同 {@code DeviceFanoutRule}。
 *
 * <h3>🔴 可信设备也要判，由 {@code bypass_by_trust = 0} 表达</h3>
 * 规则自己<b>不看</b>信任：它看的是另一件事（这台设备正在替第几个人操作）。
 * 「要不要因为可信而跳过」是配置问题，不是规则的实现细节 —— 分开之后，
 * 「一台验证过的老设备被偷了会话」这件事才判得出来。
 *
 * @Date 2026-10-16
 */
public abstract class DeviceMemberFanoutRule implements RiskRule {

    protected final LoginCounters counters;

    protected DeviceMemberFanoutRule(LoginCounters counters) {
        this.counters = counters;
    }

    protected abstract LoginCounters.Counter counter();

    /** 这个场景允许配哪几档处置（资产出口能要求核身，换绑只能限流 / 拒绝） */
    protected abstract Set<MemberRiskDecisionEnum> allowedDecisions();

    /** 为什么只能配那几档。只进报错文案 */
    protected abstract String whyLimited();

    /** 给人看的操作名，只进命中依据（「做过资产出口」/「换过联系方式」） */
    protected abstract String operationName();

    /** 这几个场景都只有一个决策点 —— 没有「验完再判一次」的位置 */
    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.duration(params, "window", Duration.ofDays(1));
        RiskParams.requireDecisionsIn(params, scenarioName(), allowedDecisions(), whyLimited());
    }

    /** 报错文案里的场景名。默认取枚举上的描述 */
    protected String scenarioName() {
        return eventType().getDesc();
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getDeviceId() == null || ctx.memberId() == null) {
            // 没带设备令牌时这一维无从计数。这几条链路本来就会先要设备身份
            return null;
        }
        long distinct = counters.addToSet(counter(), ctx.getDeviceId(), String.valueOf(ctx.memberId()));
        List<RiskParams.Tier> tiers = RiskParams.tiers(config.params());
        RiskParams.Tier tier = RiskParams.hit(tiers, distinct);
        if (tier == null) {
            return null;
        }
        return RiskHit.decide(tier.decision(),
                counters.retryAfterSeconds(counter(), ctx.getDeviceId()),
                "本设备本窗口替 " + distinct + " 个账号" + operationName() + "（超过 " + tier.over() + "）");
    }
}
