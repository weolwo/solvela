package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.risk.config.RiskListCache;

/**
 * 重置密码场景的 IP 黑名单。判据与登录那条（{@link IpBlacklistRule}）一模一样，
 * 只是另一行配置 —— 理由见 {@link RegisterIpBlacklistRule}。
 *
 * <p>🔴 <b>它生效，不陪跑</b>：黑名单是人工判过的具体结论（「这个 IP 确定在干坏事」），
 * 而重置密码是整条链上后果最重的接口。本场景另外两条新阈值先陪跑，是因为那两个数是拍的；
 * 这一条不是。
 *
 * @Date 2026-10-16
 */
@Component
public class ResetIpBlacklistRule extends IpBlacklistRule {

    public ResetIpBlacklistRule(RiskListCache lists) {
        super(lists);
    }

    @Override
    public String code() {
        return "RESET_IP_BLACKLIST";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.RESET_PASSWORD;
    }
}
