package solvela.member.risk.rule;

import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 「某个维度在一个窗口里超了多少次 → 给哪一档处置」的公共实现。IP 三条、设备两条、一机多号都是它。
 *
 * <h3>分级的意义</h3>
 * 同一个计数器配两档：超一点先出滑块，超很多才限流。一刀切成限流的话，
 * 校园网、公司出口后面一个人多试几次，整片真人跟着被挡在外面（方案 §4.1）。
 *
 * <h3>🔴 只读还是读完 +1，是每条规则自己的性质</h3>
 * 「登录请求总数」要在判断这一步 +1（这一次请求本身就算一次）；
 * 「登录失败数」必须<b>只读不加</b> —— 在这里 +1 的话，每一次正常登录也被算成一次失败。
 * 这不是能配的东西，所以是一个抽象方法而不是参数。
 *
 * @Date 2026-10-15
 */
public abstract class TieredCountRule implements RiskRule {

    protected final LoginCounters counters;

    protected TieredCountRule(LoginCounters counters) {
        this.counters = counters;
    }

    protected abstract LoginCounters.Counter counter();

    /** 这个计数器按什么维度分桶（IP、设备号……）；取不到值时返回 null，本规则直接不判 */
    protected abstract String dimensionValue(RiskContext ctx);

    /** true = 判断时把这一次也算进去；false = 只读 */
    protected abstract boolean countsThisRequest();

    /** 给人看的维度名，只进命中依据 */
    protected abstract String dimensionName();

    @Override
    public void validate(JsonNode params) {
        RiskParams.tiers(params);
        RiskParams.duration(params, "window", java.time.Duration.ofHours(1));
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        String value = dimensionValue(ctx);
        if (value == null || value.isBlank()) {
            // 维度取不到（没带设备令牌、内部工具没传 IP）—— 不判，而不是当成 0 次或无限次
            return null;
        }
        long count = countsThisRequest()
                ? counters.bump(counter(), value)
                : counters.read(counter(), value);
        List<RiskParams.Tier> tiers = RiskParams.tiers(config.params());
        // 老设备豁免：限流及以上的那几档当作没配，滑块档照常（见 limitCeiling）
        RiskParams.Tier tier = RiskParams.hit(tiers, count, limitCeiling(ctx));
        if (tier == null) {
            return null;
        }
        long retryAfter = counters.retryAfterSeconds(counter(), value);
        return decorate(RiskHit.decide(tier.decision(), retryAfter,
                dimensionName() + "本窗口第 " + count + " 次（超过 " + tier.over() + "）"), config);
    }

    /**
     * 这一次最多能落到哪一档（不含）。默认不设上限。
     *
     * <p>IP 维度的规则用它实现「老设备豁免限流档」（方案 §4.1）——
     * 做成一个钩子而不是基类自己去问豁免，是因为<b>设备维度的规则不该有这个豁免</b>：
     * 那几条挡的正是「这台设备在挨个试号」，而「这台设备很老」恰恰不是免责理由。
     */
    protected solvela.enums.MemberRiskDecisionEnum limitCeiling(RiskContext ctx) {
        return null;
    }

    /** 留给子类挂动作（比如设备闸命中时请求降档）。默认什么都不加 */
    protected RiskHit decorate(RiskHit hit, RiskRuleConfig config) {
        return hit;
    }
}
