package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;

/**
 * 全局滑块：开着时<b>所有密码登录都要滑块</b>，矩阵的「熟悉网络免滑块」不起作用。
 *
 * <h3>它取代的是一个配置项</h3>
 * 改造前这件事由 {@code solvela.app.captcha.password-login} 说，改一次要发版。
 * 现在它是规则表里的一行，后台一个开关、30 秒内生效 —— <b>被攻击时可以随时一键打开</b>，
 * 这就是应急档（方案 §3.5）。
 *
 * <h3>🔴 为什么是 alwaysEnforce</h3>
 * 总开关切到 shadow 是为了「某条规则大面积误伤、又来不及定位是哪条」。
 * 那一刻最不该顺手打开的就是这道门 —— 它恰好是量大时唯一还拦得住脚本的东西。
 *
 * <p>开关本身就是 {@code mode}：生效 = 开着，陪跑 / 关闭 = 关着。所以这里无条件命中，
 * 档位由引擎处理。
 *
 * @Date 2026-10-15
 */
@Component
public class GlobalCaptchaRule implements RiskRule {

    @Override
    public String code() {
        return "GLOBAL_CAPTCHA";
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
        return RiskHit.decide(config.directDecision(), "全局滑块开着");
    }
}
