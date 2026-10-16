package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskPhaseEnum;
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
 * 一台设备在窗口内给<b>几个不同会员</b>做过资产出口 —— 一机多号的资产侧版本。
 *
 * <h3>🔴 这件事以前一个计数器都数不到</h3>
 * {@code DEVICE_FANOUT} 只在<b>登录</b>时 add，而买号的人完全可以在每个号上只登录一次，
 * 然后在同一台设备上挨个把它们搬空 —— 登录侧的碰号数一次都不会超。
 *
 * <h3>🔴 它正是「可信设备也要验」的那一条</h3>
 * {@code MemberStepUpService.check} 回答「这台设备可信吗」，它不回答
 * 「这一次操作像不像出事了」。一台「验证过」的老设备被偷了会话之后照样可信，
 * 而这条规则看的是另一件事：这台设备正在替<b>第几个人</b>搬东西。
 * 所以它的 {@code bypass_by_trust = 0}。
 *
 * <h3>给 OTP 档就敢直接生效，给硬拦就不敢</h3>
 * OTP 是<b>可挽回</b>的：误判一次，用户多收一封信、多输六位数；
 * 而误判一次 DENY，他的东西就取不出来了，也不知道为什么。
 * 所以这条新拍的阈值上线即生效（阈值给得宽），而硬拦那两档留给人工签过字的
 * 黑名单与设备封禁。
 *
 * <h3>关联关系无论放不放行都要记下</h3>
 * 先 add 再判阈值 —— 「超了就不记」会让这台设备的真实量级被抹平，
 * 而那正是攻击时最该看清的数。判据同 {@code DeviceFanoutRule}。
 *
 * @Date 2026-10-16
 */
@Component
public class AssetDeviceFanoutRule implements RiskRule {

    private final LoginCounters counters;

    public AssetDeviceFanoutRule(LoginCounters counters) {
        this.counters = counters;
    }

    @Override
    public String code() {
        return "ASSET_DEVICE_FANOUT";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.ASSET_OUT;
    }

    /** 资产出口只有一个决策点 —— 这条链路上没有「验完再判一次」的位置 */
    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.duration(params, "window", Duration.ofDays(1));
        RiskParams.requireDecisionsIn(params, "资产出口", RiskParams.WITH_STEP_UP,
                "滑块在这条链路上没有出图与核销的地方，密码冷却只锁密码通道（这里的人已经登录了）");
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getDeviceId() == null || ctx.memberId() == null) {
            // 没带设备令牌时这一维无从计数。资产出口那边本来就会按 NO_DEVICE 要求补设备身份
            return null;
        }
        long distinct = counters.addToSet(LoginCounters.Counter.ASSET_DEVICE_MEMBER,
                ctx.getDeviceId(), String.valueOf(ctx.memberId()));
        List<RiskParams.Tier> tiers = RiskParams.tiers(config.params());
        RiskParams.Tier tier = RiskParams.hit(tiers, distinct);
        if (tier == null) {
            return null;
        }
        return RiskHit.decide(tier.decision(),
                counters.retryAfterSeconds(LoginCounters.Counter.ASSET_DEVICE_MEMBER, ctx.getDeviceId()),
                "本设备本窗口替 " + distinct + " 个账号做过资产出口（超过 " + tier.over() + "）");
    }
}
