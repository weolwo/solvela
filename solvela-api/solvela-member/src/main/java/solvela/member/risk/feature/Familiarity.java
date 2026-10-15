package solvela.member.risk.feature;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskIpFamiliarity;
import tools.jackson.databind.JsonNode;

/**
 * 算「这一次的 IP 对这个账号有多熟」。前置矩阵与后置矩阵共用同一个答案。
 *
 * <h3>🔴 一次尝试只能算出一个熟悉度</h3>
 * 前置只认严格（同网段），后置放宽到同城市 + 同运营商 —— 但那是<b>怎么用</b>的区别，
 * 不是<b>算出什么</b>的区别。算两遍的话，流水里 {@code ip_familiar} 那一列就说不清是哪一次的结果。
 * 所以这里算出完整的档位（严格 / 宽松 / 陌生），由各自的规则决定认哪一档。
 *
 * @Date 2026-10-15
 */
@Component
@RequiredArgsConstructor
public class Familiarity {

    private static final int DEFAULT_LOOKBACK_DAYS = 90;

    private final IpIntel ipIntel;

    private final LoginProfileProvider profileProvider;

    /** 算出熟悉度并记进上下文（给流水那一列用） */
    public RiskIpFamiliarity of(RiskContext ctx, JsonNode params) {
        RiskIpFamiliarity cached = ctx.getIpFamiliar();
        if (cached != null) {
            return cached;
        }
        RiskIpFamiliarity familiarity = compute(ctx, params);
        ctx.ipFamiliar(familiarity);
        return familiarity;
    }

    public IpIntel.Location location(RiskContext ctx, JsonNode params) {
        return ctx.feature("ipLocation", () -> ipIntel.resolve(ctx.getClientIp(),
                RiskParams.positiveInt(params, "v4Prefix", 24),
                RiskParams.positiveInt(params, "v6Prefix", 64)));
    }

    public LoginProfile profile(RiskContext ctx, JsonNode params) {
        return profileProvider.of(ctx, RiskParams.positiveInt(params, "lookbackDays", DEFAULT_LOOKBACK_DAYS));
    }

    private RiskIpFamiliarity compute(RiskContext ctx, JsonNode params) {
        if (ctx.memberId() == null) {
            // 查无此人：谈不上「他的常用网络」。按陌生处理 —— 而这不会泄露任何东西，
            // 因为熟悉度只被用来【放宽】（方案 §3.1），陌生就是那个默认值
            return RiskIpFamiliarity.UNFAMILIAR;
        }
        IpIntel.Location location = location(ctx, params);
        if (location.prefix() == null && location.cityIspKey() == null) {
            return RiskIpFamiliarity.UNFAMILIAR;
        }
        LoginProfile profile = profile(ctx, params);
        if (profile.knownPrefix(location.prefix())) {
            return RiskIpFamiliarity.STRICT;
        }
        return profile.knownPlace(location.cityIspKey())
                ? RiskIpFamiliarity.LOOSE
                : RiskIpFamiliarity.UNFAMILIAR;
    }
}
