package solvela.member.risk.rule;

import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import tools.jackson.databind.JsonNode;

/**
 * 重置密码场景的分级计数规则。与注册那一套（{@link RegisterTieredCountRule}）形状完全一样
 * —— 只有前置、处置只能是放行 / 限流 / 拒绝。
 *
 * <h3>🔴 但它拦错人的代价不一样</h3>
 * 注册被挡下的人还能过一会儿再来；<b>重置密码被挡下的人已经进不去了</b> ——
 * 这条路就是他最后一条自助出路。所以这两条阈值刻意给得宽，而且先陪跑：
 * 「拍一个数直接开拦」在别的场景是误伤几个人，在这里是把人锁在门外。
 *
 * <p>反过来也要说清楚：这一维以前<b>完全不存在</b>。重置密码只受「单个邮箱 / 手机号
 * 每天能发几条码」约束，而那是按<b>目标</b>算的 —— 一台机器拿着泄露的身份库挨个试，
 * 每个目标只试一次，一条限额都碰不到。
 *
 * @Date 2026-10-16
 */
public abstract class ResetTieredCountRule extends TieredCountRule {

    protected ResetTieredCountRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public final MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.RESET_PASSWORD;
    }

    @Override
    public final MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        super.validate(params);
        RiskParams.requireGateOnly(params, "重置密码");
    }
}
