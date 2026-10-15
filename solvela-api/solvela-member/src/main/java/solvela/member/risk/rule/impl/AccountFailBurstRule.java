package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.AccountFailTracker;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

/**
 * 这个账号正在被<b>多个来源</b>围攻。
 *
 * <h3>它补的是密码冷却数不到的那一类</h3>
 * 冷却数的是次数：攻击者从五个 IP 各试两次，每个桶都没到阈值，冷却什么都看不见。
 * 而「一小时内、同一个号、来自好几个不同 IP 的失败」只可能是有人在围攻它。
 *
 * <p>两个条件<b>都要满足</b>：次数够、来源够。只看次数的话，一个自己输错几遍的人就会命中。
 *
 * @Date 2026-10-15
 */
@Component
public class AccountFailBurstRule implements RiskRule {

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(1);

    private static final int DEFAULT_MIN_FAILS = 3;

    private static final int DEFAULT_MIN_IPS = 2;

    private final AccountFailTracker tracker;

    public AccountFailBurstRule(AccountFailTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public String code() {
        return "ACCOUNT_FAIL_BURST";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.POST;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.duration(params, "window", DEFAULT_WINDOW);
        RiskParams.positiveInt(params, "minFails", DEFAULT_MIN_FAILS);
        RiskParams.positiveInt(params, "minIps", DEFAULT_MIN_IPS);
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.memberId() == null) {
            return null;
        }
        Duration window = RiskParams.duration(config.params(), "window", DEFAULT_WINDOW);
        AccountFailTracker.Burst burst = tracker.burst(ctx.memberId(), window);
        int minFails = RiskParams.positiveInt(config.params(), "minFails", DEFAULT_MIN_FAILS);
        int minIps = RiskParams.positiveInt(config.params(), "minIps", DEFAULT_MIN_IPS);
        if (burst.failures() < minFails || burst.distinctIps() < minIps) {
            return null;
        }
        return RiskHit.score("近期失败 " + burst.failures() + " 次，来自 " + burst.distinctIps() + " 个不同 IP");
    }
}
