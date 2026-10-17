package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

/**
 * 一台设备碰过多少个<b>不同会员</b>。迁自 {@code DeviceGuard} 原来的 {@code checkMemberFanout}（改造前是 dry-run；那个方法 2026-10-17 已删）。
 *
 * <h3>设备维度里信号最强的一条</h3>
 * 一机多号是养号、买号的必然特征，而正常用户换号登录是极少数（家人共用一台平板、换了新号）。
 * 正因为强，误伤也最刺眼 —— 所以阈值给到 3 而不是 1。
 *
 * <h3>🔴 关联关系无论放不放行都要记下</h3>
 * 陪跑期间要的正是这份数据。所以先 add 再判阈值，而不是「超了就不记」。
 *
 * <h3>🔴 位置比改造前更早</h3>
 * 改造前它在 {@code completeLogin} 里判（那时才拿得到 memberId），也就是<b>签完凭票之后</b>。
 * 现在它在后置决策里，效果是「一机多号的设备连验证码都要不到」—— 比改造前更严。
 * 它上线时是陪跑档，所以切过去的当天没有行为差异。
 *
 * @Date 2026-10-15
 */
@Component
public class DeviceFanoutRule implements RiskRule {

    private final LoginCounters counters;

    public DeviceFanoutRule(LoginCounters counters) {
        this.counters = counters;
    }

    @Override
    public String code() {
        return "DEVICE_FANOUT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.POST;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.tiers(params);
        RiskParams.duration(params, "window", Duration.ofDays(1));
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getDeviceId() == null || ctx.memberId() == null) {
            return null;
        }
        long distinct = counters.addToSet(LoginCounters.Counter.DEVICE_MEMBER,
                ctx.getDeviceId(), String.valueOf(ctx.memberId()));
        List<RiskParams.Tier> tiers = RiskParams.tiers(config.params());
        RiskParams.Tier tier = RiskParams.hit(tiers, distinct);
        if (tier == null) {
            return null;
        }
        RiskHit hit = RiskHit.decide(tier.decision(),
                counters.retryAfterSeconds(LoginCounters.Counter.DEVICE_MEMBER, ctx.getDeviceId()),
                "本设备本窗口碰过 " + distinct + " 个不同账号（超过 " + tier.over() + "）");
        return config.params().path("observe").asBoolean(false) ? hit.withActions(RiskAction.OBSERVE_DEVICE) : hit;
    }
}
