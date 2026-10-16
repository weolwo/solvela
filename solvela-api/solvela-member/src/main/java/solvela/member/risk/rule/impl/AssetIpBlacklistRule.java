package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.risk.config.RiskListCache;

/**
 * 资产出口场景的 IP 黑名单。判据同登录那条（{@link IpBlacklistRule}），单独一行配置
 * —— 理由见 {@link RegisterIpBlacklistRule}。
 *
 * <p>🔴 这里给的是<b>硬拦</b>，而不是「要求核身」：黑名单是人工判过的具体结论
 * （「这个 IP 确定在干坏事」），让他验一道码等于告诉他「再想想办法」。
 * 而误拦一个真人的代价在这里是可控的 —— 他换个网络就能继续，
 * 而黑名单条目本身默认 30 天到期。
 *
 * @Date 2026-10-16
 */
@Component
public class AssetIpBlacklistRule extends IpBlacklistRule {

    public AssetIpBlacklistRule(RiskListCache lists) {
        super(lists);
    }

    @Override
    public String code() {
        return "ASSET_IP_BLACKLIST";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.ASSET_OUT;
    }
}
