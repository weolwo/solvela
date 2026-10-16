package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.DeviceMemberFanoutRule;

import java.util.Set;

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
 * @Date 2026-10-16
 */
@Component
public class AssetDeviceFanoutRule extends DeviceMemberFanoutRule {

    public AssetDeviceFanoutRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "ASSET_DEVICE_FANOUT";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.ASSET_OUT;
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.ASSET_DEVICE_MEMBER;
    }

    /** 资产出口是唯一一个能要求核身的非登录场景 —— 二次验证本来就长在这条链路上 */
    @Override
    protected Set<MemberRiskDecisionEnum> allowedDecisions() {
        return RiskParams.WITH_STEP_UP;
    }

    @Override
    protected String whyLimited() {
        return "滑块在这条链路上没有出图与核销的地方，密码冷却只锁密码通道（这里的人已经登录了）";
    }

    @Override
    protected String operationName() {
        return "做过资产出口";
    }
}
