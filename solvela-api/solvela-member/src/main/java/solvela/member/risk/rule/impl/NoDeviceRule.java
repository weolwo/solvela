package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;

/**
 * 请求没带有效的设备令牌 —— 一个<b>弱</b>信号。
 *
 * <h3>它针对的是谁</h3>
 * 真正不带令牌的是老客户端和<b>直接打接口的脚本</b>。清缓存、无痕模式的结果是
 * 「领一块新铭牌」，那命中的是新设备，不是这一条。
 *
 * <h3>🔴 为什么分值给得很低</h3>
 * 设备令牌还在 observe 档（不带令牌照常放行），灰度没走完之前这一条会命中一批真实用户。
 * 它的意义是「和别的信号叠起来刚好够一档」，不是自己拦人。
 *
 * <p>⚠️ 也正因为没有令牌，设备维度的所有计数对这类请求全部落空 ——
 * 这一条是那个盲区的过渡期补丁，不是替代品。真正的解法是设备令牌切 enforce。
 *
 * @Date 2026-10-15
 */
@Component
public class NoDeviceRule implements RiskRule {

    @Override
    public String code() {
        return "NO_DEVICE";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        return SolvelaStringUtil.isBlank(ctx.getDeviceId()) ? RiskHit.score("请求没带设备令牌") : null;
    }
}
