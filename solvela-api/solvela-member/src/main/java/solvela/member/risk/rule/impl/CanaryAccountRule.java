package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import solvela.member.risk.security.CanaryAccountService;

/**
 * 登录的是蜜罐账号（{@code register_source = CANARY}）→ 请求 {@link RiskAction#CANARY_TRAP}：罚 IP 与设备、投事件、告警。
 *
 * <h3>🔴 它不给处置</h3>
 * 表里配的固定处置是「放行」。拦下来、回一句别的，对方立刻知道这个号不一样；
 * 放它往下走，它在密码比对那一步失败（蜜罐的密码谁也不知道），回答与真实账号输错密码逐字节相同。
 * 这也守住了前置决策的那条约束：账号信息只能把结论往松里调，不能让「有号 / 没号」的回答不一样（风控方案 §3.1）。
 *
 * <h3>alwaysEnforce</h3>
 * 保命开关（总开关切陪跑）不该把它也关掉：它从不误伤真人 —— 没有真人会用蜜罐账号登录。
 *
 * @Date 2026-10-17
 */
@Component
public class CanaryAccountRule implements RiskRule {

    @Override
    public String code() {
        return "CANARY_ACCOUNT";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public boolean alwaysEnforce() {
        return true;
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getMember() == null || !CanaryAccountService.REGISTER_SOURCE.equals(ctx.getMember().getRegisterSource())) {
            return null;
        }
        return RiskHit.decide(config.directDecision(), "蜜罐账号 #" + ctx.getMember().getMemberId())
                .withActions(RiskAction.CANARY_TRAP);
    }
}
