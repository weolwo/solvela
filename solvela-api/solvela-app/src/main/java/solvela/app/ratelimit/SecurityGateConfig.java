package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PatternParseException;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全闸的全局参数：{@code t_config.gateway_security_gate}，随规则快照下发（网关方案 §5、§6.1）。
 *
 * <pre>{@code
 * {"ban":"24h", "breaker":{"window":"10m","distinctIps":50},
 *  "honeypotPaths":[{"code":"ENV","path":"/.env","enabled":true}, ...], ...}
 * }</pre>
 * 其余字段（黑到期转灰、同段连坐、关键字）由各自的任务解释，这里不认识的一律忽略。
 *
 * <h3>解不开就退回默认值，不是「不设防」</h3>
 * 单条蜜罐路径写坏了只丢那一条并 WARN；整段 JSON 坏了用默认参数、<b>没有蜜罐</b> ——
 * 蜜罐是「多一道」，没有它不影响别的闸；而一条写错的蜜罐路径可能把真人送进 24 小时，宁缺毋滥。
 *
 * @param banSeconds          蜜罐 / 关键字命中后 IP 与设备的处罚时长
 * @param breakerWindowSeconds 熔断的统计窗口
 * @param breakerDistinctIps  窗口内超过这么多个不同 IP 命中蜜罐 → 停止处罚（更可能是我们自己出了问题）
 * @Date 2026-10-17
 */
@Slf4j
record SecurityGateConfig(long banSeconds, long breakerWindowSeconds, int breakerDistinctIps,
                          List<Honeypot> honeypots) {

    static final SecurityGateConfig DEFAULTS = new SecurityGateConfig(86400, 600, 50, List.of());

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 编码进日志与事件（{@code t_security_incident.rule_code} 宽 32） */
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,32}");

    /** 一条蜜罐路径。{@code path} 是 Spring 的路径模板（{@code /.git/**} 这种），按解码后的路径匹配 */
    record Honeypot(String code, String path, PathPattern pattern) {

        boolean matches(PathContainer path) {
            return pattern.matches(path);
        }
    }

    static SecurityGateConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULTS;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("【安全闸】gateway_security_gate 不是合法 JSON，用默认参数且不启用蜜罐: {}", json);
            return DEFAULTS;
        }
        long ban = duration(root.get("ban"), DEFAULTS.banSeconds, "ban");
        JsonNode breaker = root.path("breaker");
        long window = duration(breaker.get("window"), DEFAULTS.breakerWindowSeconds, "breaker.window");
        JsonNode distinct = breaker.get("distinctIps");
        int distinctIps = distinct != null && distinct.canConvertToInt() && distinct.asInt() > 0
                ? distinct.asInt() : DEFAULTS.breakerDistinctIps;
        return new SecurityGateConfig(ban, window, distinctIps, parseHoneypots(root.get("honeypotPaths")));
    }

    /** 请求路径命中的蜜罐；没命中为 null */
    Honeypot honeypot(String requestPath) {
        if (honeypots.isEmpty() || requestPath == null) {
            return null;
        }
        PathContainer path = PathContainer.parsePath(requestPath);
        for (Honeypot h : honeypots) {
            if (h.matches(path)) {
                return h;
            }
        }
        return null;
    }

    /**
     * 与真实路由重叠的蜜罐（网关方案 §5.3 第 3 条）。
     *
     * <p>两个路径模板「有没有交集」没有现成的判法，这里两头各取一个样本互相匹配：
     * 把路由模板里的变量、通配符换成一个具体段，看蜜罐认不认；反过来也一样。
     * 对蜜罐这种几乎都是字面路径（{@code /.env}）或前缀（{@code /.git/**}）的模板足够准。
     *
     * @param routePatterns 本进程全部接口的路径模板（{@code /activity/{activityCode}/draw}）
     */
    List<Honeypot> overlapping(Collection<String> routePatterns) {
        List<Honeypot> result = new ArrayList<>();
        for (Honeypot h : honeypots) {
            PathContainer honeypotSample = PathContainer.parsePath(sample(h.path()));
            for (String route : routePatterns) {
                PathPattern routePattern;
                try {
                    routePattern = PathPatternParser.defaultInstance.parse(route);
                } catch (PatternParseException e) {
                    continue;
                }
                if (routePattern.matches(honeypotSample) || h.matches(PathContainer.parsePath(sample(route)))) {
                    result.add(h);
                    break;
                }
            }
        }
        return result;
    }

    /** 去掉某几条之后的配置（运行中加载到与路由重叠的蜜罐时用） */
    SecurityGateConfig without(Collection<Honeypot> removed) {
        List<Honeypot> kept = honeypots.stream().filter(h -> !removed.contains(h)).toList();
        return new SecurityGateConfig(banSeconds, breakerWindowSeconds, breakerDistinctIps, kept);
    }

    /** 模板 → 一个能被它匹配的具体路径：变量与通配符各换成一段 {@code x} */
    static String sample(String template) {
        return template.replaceAll("\\{[^}]*}", "x").replace("**", "x").replace("*", "x");
    }

    private static List<Honeypot> parseHoneypots(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Honeypot> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.path("enabled").asBoolean(true)) {
                continue;
            }
            String code = item.path("code").asString("");
            String path = item.path("path").asString("");
            if (!CODE.matcher(code).matches() || !path.startsWith("/")) {
                log.warn("【安全闸】蜜罐路径配置不合法（编码要大写字母数字下划线、路径以 / 开头），忽略: {}", item);
                continue;
            }
            try {
                result.add(new Honeypot(code, path, PathPatternParser.defaultInstance.parse(path)));
            } catch (PatternParseException e) {
                log.warn("【安全闸】蜜罐路径解不开，忽略: {}", item);
            }
        }
        return List.copyOf(result);
    }

    private static long duration(JsonNode node, long fallback, String field) {
        long s = node == null || !node.isString() ? -1 : RateLimitGlobals.seconds(node.asString());
        if (s <= 0) {
            if (node != null) {
                log.warn("【安全闸】gateway_security_gate.{} 解不开（{}），用默认值 {} 秒", field, node, fallback);
            }
            return fallback;
        }
        return s;
    }
}
