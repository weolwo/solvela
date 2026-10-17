package solvela.app.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriUtils;
import solvela.security.KeywordRules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 安全闸第 ③ 步的「找」：在一个请求里找第一处关键字命中（网关方案 §5.4）。罚不罚由 {@code SecurityGateFilter} 决定。
 *
 * <p>扫的顺序与位置名（事件里的 {@code matched_field} / {@code matched_param} 由位置名推出来）：
 * <ol>
 *   <li>{@code User-Agent}，只给 UA 规则看 → {@code ua}；</li>
 *   <li>路径，URL 解码一次 → {@code path}；</li>
 *   <li>查询串的每个值，URL 解码一次 → {@code query.<参数名>}；</li>
 *   <li>JSON 请求体的每个字符串值 → {@code body.<字段路径>}，如 {@code body.items[1].name}。</li>
 * </ol>
 * 🔴 密码、验证码一类字段（{@link KeywordRules#isSecretField}）不参与匹配。
 *
 * @Date 2026-10-17
 */
final class KeywordScanner {

    /** 只用来读请求体的树，不塑造任何输出 */
    private static final JsonMapper BODY_MAPPER = JsonMapper.builder().build();

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private KeywordScanner() {
    }

    /**
     * @param body 已读出的 JSON 请求体；不扫请求体为 null
     * @return 第一处命中；没命中为 null
     */
    static KeywordRules.Hit scan(List<KeywordRules.Rule> rules, String requestUri, String queryString, byte[] body,
                                 String userAgent) {
        if (rules.isEmpty()) {
            return null;
        }
        return Stream.<Supplier<KeywordRules.Hit>>of(
                        () -> KeywordRules.find(rules, KeywordRules.Scope.UA, userAgent, "ua"),
                        () -> KeywordRules.find(rules, KeywordRules.Scope.PARAM, decodePath(requestUri), "path"),
                        () -> scanQuery(rules, queryString),
                        () -> scanBody(rules, body))
                .map(Supplier::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 要扫的 JSON 请求体：有请求体的方法、{@code Content-Type} 是 JSON、声明了长度且不超过 {@link KeywordRules#MAX_BODY_BYTES}。
     * 不满足的返回 null（不扫）—— 分块传输、超大的、文件上传与表单都不扫，成本不成比例。
     * 读过的请求体要用 {@link CachedBodyRequest} 包一层再往下传。
     */
    static byte[] readSmallJsonBody(HttpServletRequest request) throws IOException {
        String type = request.getContentType();
        long length = request.getContentLengthLong();
        boolean scannable = BODY_METHODS.contains(request.getMethod())
                && type != null && type.toLowerCase(Locale.ROOT).contains("json")
                && length > 0 && length <= KeywordRules.MAX_BODY_BYTES;
        return scannable ? request.getInputStream().readNBytes((int) length) : null;
    }

    private static KeywordRules.Hit scanQuery(List<KeywordRules.Rule> rules, String queryString) {
        if (queryString == null) {
            return null;
        }
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String name = decodeQuery(eq < 0 ? pair : pair.substring(0, eq));
            if (eq < 0 || KeywordRules.isSecretField(name)) {
                continue;
            }
            KeywordRules.Hit hit = KeywordRules.find(rules, KeywordRules.Scope.PARAM,
                    decodeQuery(pair.substring(eq + 1)), "query." + name);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static KeywordRules.Hit scanBody(List<KeywordRules.Rule> rules, byte[] body) {
        if (body == null) {
            return null;
        }
        try {
            return scanJson(rules, BODY_MAPPER.readTree(body), "body");
        } catch (Exception e) {
            // 解不开的 JSON 由参数绑定回 400，这里不必再看
            return null;
        }
    }

    private static KeywordRules.Hit scanJson(List<KeywordRules.Rule> rules, JsonNode node, String location) {
        if (node == null) {
            return null;
        }
        if (node.isString()) {
            return KeywordRules.find(rules, KeywordRules.Scope.PARAM, node.asString(), location);
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                KeywordRules.Hit hit = scanJson(rules, node.get(i), location + "[" + i + "]");
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            KeywordRules.Hit hit = KeywordRules.isSecretField(field.getKey()) ? null
                    : scanJson(rules, field.getValue(), location + "." + field.getKey());
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 路径解码一次；坏的百分号编码按原样扫 */
    private static String decodePath(String path) {
        try {
            return UriUtils.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return path;
        }
    }

    /** 查询串里的一段解码一次（{@code +} 是空格）；坏的百分号编码按原样扫 */
    private static String decodeQuery(String part) {
        try {
            return URLDecoder.decode(part, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return part;
        }
    }
}
