package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.risk.config.RiskListCache;

/**
 * 注册场景的 IP 黑名单。<b>判据与登录那条（{@link IpBlacklistRule}）一模一样，只是另一行配置。</b>
 *
 * <h3>为什么不是一条规则挂两个场景</h3>
 * 一条规则只属于一个场景，这是刻意的（见 {@code MemberRiskEventTypeEnum}）：
 * 规则表里一行就是<b>一个可以单独开关、单独调参的闸门</b>。共用一行的话，
 * 「注册这边误伤太多、先关掉」这个动作会顺手把登录那边也关掉 ——
 * 而那正是最不该在同一个开关上的两件事。
 *
 * <p>代价是同一份证据有两行配置，可能被配成不一致。这个代价是值得的：
 * 不一致是<b>看得见的</b>（规则页上两行并排），而共用一行导致的连带关闭是看不见的。
 *
 * <p>所以这里直接继承登录那条，<b>只改编码和场景</b> —— 判定逻辑没有第二份。
 *
 * @Date 2026-10-16
 */
@Component
public class RegisterIpBlacklistRule extends IpBlacklistRule {

    public RegisterIpBlacklistRule(RiskListCache lists) {
        super(lists);
    }

    @Override
    public String code() {
        return "REG_IP_BLACKLIST";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.REGISTER;
    }
}
