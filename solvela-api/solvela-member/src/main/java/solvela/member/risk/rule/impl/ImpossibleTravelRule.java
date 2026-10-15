package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.Familiarity;
import solvela.member.risk.feature.IpIntel;
import solvela.member.risk.feature.LoginProfile;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 不可能旅行：上一次成功登录在深圳，20 分钟后出现在纽约。
 *
 * <h3>🔴 唯一不被可信设备绕过的打分规则</h3>
 * 设备令牌是<b>能被复制</b>的（{@code MemberStepUpService} 类注释里的已知边界）。
 * 一台「老设备」在 20 分钟内跨越半个地球，正是令牌被复制的样子 ——
 * 而那恰恰是可信设备这一档唯一挡不住的攻击。所以表里它的 {@code bypass_by_trust = 0}。
 *
 * <h3>⚠️ 噪声大，所以判据收得很紧</h3>
 * 移动网络的出口 IP 常常落在省会，手机切一次基站、开关一次飞行模式，「城市」就能跳一次。
 * 所以只看<b>跨国 + 短间隔</b>与<b>跨省 + 极短间隔</b>，城市一级完全不看。
 *
 * <p>更彻底的降噪是「新旧任一方是蜂窝网络就不判」，但 ip2region 给不出网络类型
 * （它的 ISP 字段是「电信 / 联通 / 移动」，同一个运营商既有宽带也有 4G）。
 * 等接了能给 usage type 的商业 IP 情报再打开（P3）。
 *
 * @Date 2026-10-15
 */
@Component
public class ImpossibleTravelRule implements RiskRule {

    private static final int DEFAULT_CROSS_COUNTRY_MINUTES = 120;

    private static final int DEFAULT_CROSS_PROVINCE_MINUTES = 30;

    private final Familiarity familiarity;

    public ImpossibleTravelRule(Familiarity familiarity) {
        this.familiarity = familiarity;
    }

    @Override
    public String code() {
        return "IMPOSSIBLE_TRAVEL";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.POST;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.positiveInt(params, "crossCountryMinutes", DEFAULT_CROSS_COUNTRY_MINUTES);
        RiskParams.positiveInt(params, "crossProvinceMinutes", DEFAULT_CROSS_PROVINCE_MINUTES);
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        IpIntel.Location now = familiarity.location(ctx, config.params());
        if (now.unknown() || now.intranet()) {
            return null;
        }
        LoginProfile.LastSuccess last = familiarity.profile(ctx, config.params()).lastSuccess();
        if (last == null || last.time() == null || last.country() == null) {
            return null;
        }
        Duration gap = Duration.between(last.time(), LocalDateTime.now());
        if (gap.isNegative()) {
            // 上一次登录在「未来」—— 只可能是时钟或时区出了问题。不判，而不是当成 0 分钟
            return null;
        }

        if (!last.country().equals(now.country())) {
            long minutes = RiskParams.positiveInt(config.params(), "crossCountryMinutes",
                    DEFAULT_CROSS_COUNTRY_MINUTES);
            return gap.toMinutes() < minutes
                    ? RiskHit.score(describe(last.country(), now.country(), gap))
                    : null;
        }
        // 同国才比省。省份任一方缺失时就到此为止 —— 拿 null 去比会把它判成「换省了」
        if (last.province() == null || now.province() == null || last.province().equals(now.province())) {
            return null;
        }
        long minutes = RiskParams.positiveInt(config.params(), "crossProvinceMinutes",
                DEFAULT_CROSS_PROVINCE_MINUTES);
        return gap.toMinutes() < minutes
                ? RiskHit.score(describe(last.province(), now.province(), gap))
                : null;
    }

    private static String describe(String from, String to, Duration gap) {
        return "上次在 " + from + "，本次在 " + to + "，相隔 " + gap.toMinutes() + " 分钟";
    }
}
