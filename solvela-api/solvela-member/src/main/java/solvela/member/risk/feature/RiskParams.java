package solvela.member.risk.feature;

import solvela.enums.MemberRiskDecisionEnum;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 读 {@code t_member_risk_rule.params} 的小工具。
 *
 * <h3>为什么不用 Spring 的 DurationStyle</h3>
 * 这些参数是<b>后台可改</b>的，读不出来的后果是一条规则静默关闭。所以解析规则必须是我们自己说得清的、
 * 有单测钉住的那一份，而不是跟着某个框架内部类的版本走。只认 {@code s/m/h/d} 四个后缀，够用且不会有歧义。
 *
 * <h3>解析失败一律抛 {@link IllegalArgumentException}</h3>
 * 规则的 {@code validate} 与后台保存走同一条路：<b>存不进去</b>好过存进去之后静默失效。
 *
 * @Date 2026-10-15
 */
public final class RiskParams {

    private RiskParams() {
    }

    /** 分级阈值的一档：计数<b>超过</b> over 时给出 decision */
    public record Tier(long over, MemberRiskDecisionEnum decision) {
    }

    /**
     * 这个场景只许配放行 / 限流 / 拒绝 —— 滑块、OTP、冷却在它那条链路上<b>没有落点</b>。
     *
     * <h3>🔴 为什么是「存不进去」而不是「执行时忽略」</h3>
     * 配成滑块之后，那条规则会给出一个<b>没人能满足的处置</b>：
     * 表现是接口返回一个客户端不认识的结果，而规则页上那一行看着完全正常。
     * 判据与 {@code LoginMatrixRule} 拒绝配成「冷却」同一个：
     * <b>一个此刻执行不了的处置，不如在保存那一刻就拦住。</b>
     *
     * <p>注册与重置密码共用它 —— 两边的理由一字不差（票在发码那一步核销、
     * 接口本身已经验过一次码），所以校验也只该有一份。
     *
     * @param scenario 场景名，只进报错文案（「注册」/「重置密码」）
     */
    public static void requireGateOnly(JsonNode params, String scenario) {
        requireDecisionsIn(params, scenario, GATE_ONLY,
                "滑块与 OTP 在这里没有落点（票在发码那一步核销，接口本身已经验过一次码）");
    }

    /** 放行 / 限流 / 拒绝。注册与重置密码只能配这三档 */
    public static final java.util.Set<MemberRiskDecisionEnum> GATE_ONLY = java.util.Set.of(
            MemberRiskDecisionEnum.PASS, MemberRiskDecisionEnum.LIMIT, MemberRiskDecisionEnum.DENY);

    /**
     * 放行 / OTP / 换通道 OTP / 限流 / 拒绝。<b>资产出口</b>能配这几档 ——
     * 二次验证本来就长在那条链路上，所以它是唯一一个能让风控要求核身的非登录场景。
     *
     * <p>不含滑块（那条链路上没有出图与核销的地方）与密码冷却（它只锁密码通道，
     * 而这里的人已经登录了）。
     */
    public static final java.util.Set<MemberRiskDecisionEnum> WITH_STEP_UP = java.util.Set.of(
            MemberRiskDecisionEnum.PASS, MemberRiskDecisionEnum.OTP, MemberRiskDecisionEnum.OTP_STRONG,
            MemberRiskDecisionEnum.LIMIT, MemberRiskDecisionEnum.DENY);

    /**
     * 分级档位只能落在 {@code allowed} 里，否则<b>存不进去</b>。
     *
     * @param why 说清为什么不许 —— 报错文案要让看到它的人不必再去翻代码
     */
    public static void requireDecisionsIn(JsonNode params, String scenario,
                                          java.util.Set<MemberRiskDecisionEnum> allowed, String why) {
        for (Tier tier : tiers(params)) {
            if (!allowed.contains(tier.decision())) {
                throw new IllegalArgumentException(scenario + "场景不能配这一档：" + why
                        + "：over " + tier.over() + " → " + tier.decision());
            }
        }
    }

    public static Duration duration(JsonNode params, String field, Duration fallback) {
        JsonNode node = params.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        return parseDuration(node.asString(), field);
    }

    /**
     * {@code "60s"} / {@code "5m"} / {@code "1h"} / {@code "1d"}。
     *
     * <p>🔴 不接受裸数字：{@code "60"} 到底是秒还是分钟，看的人一半会猜错，而猜错不会报错。
     */
    public static Duration parseDuration(String raw, String field) {
        String text = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.length() < 2) {
            throw new IllegalArgumentException(field + " 要带单位（s/m/h/d），实际是: " + raw);
        }
        char unit = text.charAt(text.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(text.substring(0, text.length() - 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 解析不出数值: " + raw);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException(field + " 必须是正数: " + raw);
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException(field + " 的单位只认 s/m/h/d，实际是: " + raw);
        };
    }

    public static int positiveInt(JsonNode params, String field, int fallback) {
        JsonNode node = params.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (!node.isIntegralNumber() || node.asInt() <= 0) {
            throw new IllegalArgumentException(field + " 必须是正整数，实际是: " + node);
        }
        return node.asInt();
    }

    /**
     * 解析分级阈值 {@code [{"over":5,"do":2},{"over":20,"do":6}]}，按 {@code over} 升序返回。
     *
     * <h3>🔴 两条校验，都是为了防「配了却不起作用」</h3>
     * <ul>
     *   <li><b>over 必须严格递增</b>：两档配成同一个数，后一档永远轮不到；</li>
     *   <li><b>处置必须越来越严</b>。配成「超 5 次限流、超 20 次出滑块」的话，
     *       超 20 次的人<b>反而被放松了</b> —— 而页面上两行看着都很正常。</li>
     * </ul>
     */
    public static List<Tier> tiers(JsonNode params) {
        JsonNode node = params.path("tiers");
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("tiers 不能为空");
        }
        List<Tier> tiers = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            long over = item.path("over").asLong(-1);
            if (over < 0) {
                throw new IllegalArgumentException("tiers[].over 必须是非负整数，实际是: " + item);
            }
            int decision = item.path("do").asInt(-1);
            tiers.add(new Tier(over, MemberRiskDecisionEnum.ofValue(decision)));
        }
        tiers.sort((a, b) -> Long.compare(a.over(), b.over()));
        for (int i = 1; i < tiers.size(); i++) {
            Tier prev = tiers.get(i - 1);
            Tier cur = tiers.get(i);
            if (cur.over() == prev.over()) {
                throw new IllegalArgumentException("tiers 里有两档的 over 相同（后一档永远轮不到）: " + cur.over());
            }
            if (!cur.decision().atLeast(prev.decision())) {
                throw new IllegalArgumentException("tiers 的处置必须随 over 越来越严，"
                        + "否则超得更多的人反而被放松了: over " + prev.over() + " → " + prev.decision()
                        + "，over " + cur.over() + " → " + cur.decision());
            }
        }
        return List.copyOf(tiers);
    }

    /** 命中的那一档：取 {@code count > over} 里 over 最大的那个；都没超返回 null */
    public static Tier hit(List<Tier> tiers, long count) {
        return hit(tiers, count, null);
    }

    /**
     * 取命中的那一档，但<b>不超过 {@code ceiling}</b>（不含）。
     *
     * <p>给「老设备豁免 IP 限流」用（方案 §4.1）：传 {@code LIMIT} 就表示
     * 「限流及以上的那几档当作没配」，于是超得再多也只落到滑块那一档。
     *
     * <p>🔴 <b>不是「整条规则不判」</b>：计数照常加、滑块档照常出。
     * 整条跳过的话，一台七天前签发过令牌的设备就成了一张免检通行证 ——
     * 而设备令牌是匿名领的，攻击者也能提前领一批养着。
     *
     * @param ceiling 上限（不含）；null = 不设上限
     */
    public static Tier hit(List<Tier> tiers, long count, MemberRiskDecisionEnum ceiling) {
        Tier hit = null;
        for (Tier tier : tiers) {
            if (count > tier.over() && (ceiling == null || !tier.decision().atLeast(ceiling))) {
                hit = tier;
            }
        }
        return hit;
    }
}
