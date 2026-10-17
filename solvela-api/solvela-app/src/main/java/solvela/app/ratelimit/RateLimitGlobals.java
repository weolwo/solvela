package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code t_config.gateway_rate_limit} 解释成网关能直接用的样子（网关方案 §4、§2.3–§2.5）。
 *
 * <h3>任何一项解不出来，就用那一项的默认值</h3>
 * 默认值与种子数据一致。一项写错（比如后台把 {@code "5m"} 写成 {@code "5分钟"}）只让那一项退回默认值并 WARN，
 * 而不是整份参数作废 —— 更不能是「解析失败 = 不处罚」。
 *
 * @param ladderSeconds     阶梯处罚：第 n 次违规罚多久（秒），第 6 次之后一直是最后一档
 * @param levelResetSeconds 多久没有新违规，档位清零
 * @param anonAccountCapSec 匿名场景的账号维度封顶
 * @param ipSegmentCapSec   IP 段维度封顶
 * @param oldDeviceSeconds  设备令牌签发多久之后算「老设备」
 * @param greyFactor        IP 灰名单上的 IP：阈值乘以它（向上取整，至少 1）
 * @param greyLevel         限流罚到第几档起，IP 维度的那个 IP 进灰名单、匿名场景设备维度的那台设备进观察档（§5.5）
 * @param greyDays          上一条拉灰拉多少天
 * @Date 2026-10-17
 */
@Slf4j
public record RateLimitGlobals(
        List<Long> ladderSeconds,
        long levelResetSeconds,
        long anonAccountCapSec,
        long ipSegmentCapSec,
        long oldDeviceSeconds,
        double greyFactor,
        int greyLevel,
        int greyDays) {

    static final RateLimitGlobals DEFAULTS = new RateLimitGlobals(
            List.of(60L, 300L, 1800L, 3600L, 7200L, 28800L), 86400L, 1800L, 1800L, 7L * 86400, 0.5, 4, 7);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([smhd])");

    /** 解析；{@code json} 为空或整体解不开时全部用默认值 */
    static RateLimitGlobals parse(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULTS;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("【网关限流】gateway_rate_limit 不是合法 JSON，全部用默认值: {}", json);
            return DEFAULTS;
        }
        List<Long> ladder = ladder(root.get("ladder"));
        return new RateLimitGlobals(
                ladder,
                duration(root, "levelResetAfter", DEFAULTS.levelResetSeconds),
                duration(root, "anonAccountCap", DEFAULTS.anonAccountCapSec),
                duration(root, "ipSegmentCap", DEFAULTS.ipSegmentCapSec),
                root.has("oldDeviceDays") && root.get("oldDeviceDays").canConvertToLong()
                        && root.get("oldDeviceDays").asLong() > 0
                        ? root.get("oldDeviceDays").asLong() * 86400 : DEFAULTS.oldDeviceSeconds,
                root.has("greyFactor") && root.get("greyFactor").isNumber()
                        && root.get("greyFactor").asDouble() > 0 && root.get("greyFactor").asDouble() <= 1
                        ? root.get("greyFactor").asDouble() : DEFAULTS.greyFactor,
                positiveInt(root, "rateLimitGreyLevel", DEFAULTS.greyLevel),
                positiveInt(root, "rateLimitGreyDays", DEFAULTS.greyDays));
    }

    private static int positiveInt(JsonNode root, String field, int fallback) {
        JsonNode node = root.get(field);
        return node != null && node.canConvertToInt() && node.asInt() > 0 ? node.asInt() : fallback;
    }

    /** {@code "60s" / "5m" / "1h" / "1d"} → 秒；解不开返回 -1 */
    static long seconds(String text) {
        if (text == null) {
            return -1;
        }
        Matcher m = DURATION.matcher(text.trim());
        if (!m.matches()) {
            return -1;
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "s" -> n;
            case "m" -> Duration.ofMinutes(n).toSeconds();
            case "h" -> Duration.ofHours(n).toSeconds();
            default -> Duration.ofDays(n).toSeconds();
        };
    }

    private static long duration(JsonNode root, String field, long fallback) {
        JsonNode node = root.get(field);
        long s = node == null || !node.isString() ? -1 : seconds(node.asString());
        if (s <= 0) {
            if (node != null) {
                log.warn("【网关限流】gateway_rate_limit.{} 解不开（{}），用默认值 {} 秒", field, node, fallback);
            }
            return fallback;
        }
        return s;
    }

    private static List<Long> ladder(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return DEFAULTS.ladderSeconds;
        }
        List<Long> steps = new ArrayList<>();
        for (JsonNode step : node) {
            long s = step.isString() ? seconds(step.asString()) : -1;
            if (s <= 0) {
                log.warn("【网关限流】gateway_rate_limit.ladder 里有一档解不开（{}），整条阶梯用默认值", step);
                return DEFAULTS.ladderSeconds;
            }
            steps.add(s);
        }
        return List.copyOf(steps);
    }
}
