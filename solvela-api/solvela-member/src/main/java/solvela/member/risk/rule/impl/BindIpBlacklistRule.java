package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.risk.config.RiskListCache;

/**
 * 换绑场景的 IP 黑名单。判据同登录那条（{@link IpBlacklistRule}），单独一行配置。
 *
 * @Date 2026-10-16
 */
@Component
public class BindIpBlacklistRule extends IpBlacklistRule {

    public BindIpBlacklistRule(RiskListCache lists) {
        super(lists);
    }

    @Override
    public String code() {
        return "BIND_IP_BLACKLIST";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.CONTACT_BIND;
    }
}
