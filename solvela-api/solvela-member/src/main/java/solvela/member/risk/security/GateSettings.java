package solvela.member.risk.security;

import lombok.extern.slf4j.Slf4j;
import solvela.base.json.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会员域这边要用到的那几项 {@code t_config.gateway_security_gate}：处罚时长、熔断、黑到期转灰、同段连坐。
 * 解释规则与网关一致（{@code "24h"} 这种时长写法），任何一项解不开用那一项的默认值。
 *
 * @param banSeconds          蜜罐 / 关键字 / 蜜罐账号命中后 IP 与设备的处罚时长
 * @param breakerWindowSeconds 熔断窗口（后台只用来展示「最近命中了多少个不同 IP」）
 * @param breakerDistinctIps  窗口内超过多少个不同 IP 命中就熔断
 * @param escalateGreyDays    自动黑名单到期后转灰多少天
 * @param segmentMinIps       同段连坐：窗口内多少个不同 IP 被自动拉黑
 * @param segmentWithinSeconds 同段连坐的窗口
 * @param segmentDays         同段连坐拉灰多少天
 * @Date 2026-10-17
 */
@Slf4j
record GateSettings(long banSeconds, long breakerWindowSeconds, int breakerDistinctIps, int escalateGreyDays,
                    int segmentMinIps, long segmentWithinSeconds, int segmentDays) {

    static final GateSettings DEFAULTS = new GateSettings(86400, 600, 50, 30, 3, 86400, 7);

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([smhd])");

    static GateSettings parse(String json) {
        GateSettings d = DEFAULTS;
        if (json == null || json.isBlank()) {
            return d;
        }
        try {
            JsonNode root = JsonUtils.getMapper().readTree(json);
            JsonNode breaker = root.path("breaker");
            JsonNode seg = root.path("segmentGrey");
            return new GateSettings(
                    seconds(root.path("ban").asString(null), d.banSeconds()),
                    seconds(breaker.path("window").asString(null), d.breakerWindowSeconds()),
                    positive(breaker.path("distinctIps"), d.breakerDistinctIps()),
                    positive(root.path("escalateGreyDays"), d.escalateGreyDays()),
                    positive(seg.path("minIps"), d.segmentMinIps()),
                    seconds(seg.path("within").asString(null), d.segmentWithinSeconds()),
                    positive(seg.path("days"), d.segmentDays()));
        } catch (Exception e) {
            log.warn("【安全名单】gateway_security_gate 解不开，用默认参数", e);
            return d;
        }
    }

    private static int positive(JsonNode node, int fallback) {
        return node.canConvertToInt() && node.asInt() > 0 ? node.asInt() : fallback;
    }

    /** {@code "24h" / "10m"} → 秒；解不开或不是正数用 {@code fallback} */
    static long seconds(String text, long fallback) {
        if (text == null) {
            return fallback;
        }
        Matcher m = DURATION.matcher(text.trim());
        if (!m.matches()) {
            return fallback;
        }
        long n = Long.parseLong(m.group(1));
        long s = switch (m.group(2)) {
            case "s" -> n;
            case "m" -> n * 60;
            case "h" -> n * 3600;
            default -> n * 86400;
        };
        return s > 0 ? s : fallback;
    }
}
