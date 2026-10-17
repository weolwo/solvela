package solvela.member.risk.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import solvela.base.json.JsonUtils;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.base.module.config.ConfigService;
import solvela.exception.BusinessException;
import solvela.security.SecurityKeys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 后台「蜜罐与关键字」页（网关方案 §5.3、§5.4、§6.2）：蜜罐路径与关键字规则的增、删、单独停用，以及熔断状态。
 *
 * <h3>存的还是 {@code t_config.gateway_security_gate} 那一条</h3>
 * 这里只是给它一张能用的表单：每次改动都整条写回，走 {@link ConfigService#updateValueByKey} ——
 * 保存前的校验（{@link GatewaySecurityGateValidator}：关键字过反例集）与保存后的快照重发都在那条路上，不在这里另写一份。
 *
 * <h3>熔断状态是现读的</h3>
 * 读网关写的熔断集合（{@code sec:hp:breaker}、{@code sec:kw:breaker:*}）里窗口内的不同 IP 数，与阈值比。
 * 只读，不能在这里「重置熔断」：熔断是窗口滑过去自己解除的，强行清掉等于在它最可能是误伤的时候恢复处罚。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityGateAdminService {

    /** 两类条目，各存在配置 JSON 的一个数组里 */
    public enum Kind {
        HONEYPOT("honeypotPaths"), KEYWORD("keywords");

        private final String field;

        Kind(String field) {
            this.field = field;
        }
    }

    private final ConfigService configService;

    private final StringRedisTemplate redis;

    /** 一条蜜罐路径或关键字规则，带它的熔断状态 */
    public record Entry(Kind kind, String code, String path, String scope, String pattern, boolean enabled,
                        long recentDistinctIps, boolean breakerOpen) {
    }

    /** 整页 */
    public record Gate(long banSeconds, long breakerWindowSeconds, int breakerDistinctIps, List<Entry> honeypots,
                       List<Entry> keywords) {
    }

    public Gate get() {
        ObjectNode root = load();
        GateSettings settings = GateSettings.parse(root.toString());
        long window = settings.breakerWindowSeconds();
        int threshold = settings.breakerDistinctIps();
        long nowMillis = System.currentTimeMillis();

        long honeypotIps = distinct(SecurityKeys.HONEYPOT_BREAKER, window, nowMillis);
        List<Entry> honeypots = new ArrayList<>();
        for (JsonNode h : root.path("honeypotPaths")) {
            honeypots.add(new Entry(Kind.HONEYPOT, h.path("code").asString(""), h.path("path").asString(""), null, null,
                    h.path("enabled").asBoolean(true), honeypotIps, honeypotIps > threshold));
        }
        List<Entry> keywords = new ArrayList<>();
        for (JsonNode k : root.path("keywords")) {
            String code = k.path("code").asString("");
            long ips = distinct(SecurityKeys.keywordBreaker(code), window, nowMillis);
            keywords.add(new Entry(Kind.KEYWORD, code, null, k.path("scope").asString("PARAM"),
                    k.path("pattern").asString(""), k.path("enabled").asBoolean(true), ips, ips > threshold));
        }
        return new Gate(settings.banSeconds(), window, threshold, honeypots, keywords);
    }

    public void setEnabled(Kind kind, String code, boolean enabled, String operator) {
        ObjectNode root = load();
        ObjectNode entry = find(root, kind, code);
        entry.put("enabled", enabled);
        save(root);
        log.warn("【安全闸】{} {} {}, 操作人: {}", kind, code, enabled ? "启用" : "停用", operator);
    }

    /** 加一条蜜罐路径。与真实接口是否重叠由网关在加载 / 启动时判（重叠的不启用 / 拒绝启动） */
    public void addHoneypot(String code, String path, String operator) {
        ObjectNode root = load();
        requireNew(root, Kind.HONEYPOT, code);
        ObjectNode e = array(root, Kind.HONEYPOT).addObject();
        e.put("code", normalizeCode(code));
        e.put("path", path == null ? "" : path.trim());
        e.put("enabled", true);
        save(root);
        log.warn("【安全闸】新增蜜罐路径 {} = {}, 操作人: {}", code, path, operator);
    }

    /** 加一条关键字规则。存不存得进去由保存前的反例集校验决定 */
    public void addKeyword(String code, String scope, String pattern, String operator) {
        ObjectNode root = load();
        requireNew(root, Kind.KEYWORD, code);
        ObjectNode e = array(root, Kind.KEYWORD).addObject();
        e.put("code", normalizeCode(code));
        e.put("scope", scope == null ? "PARAM" : scope.trim().toUpperCase(Locale.ROOT));
        e.put("pattern", pattern == null ? "" : pattern);
        e.put("enabled", true);
        save(root);
        log.warn("【安全闸】新增关键字规则 {}（{}）, 操作人: {}", code, scope, operator);
    }

    public void remove(Kind kind, String code, String operator) {
        ObjectNode root = load();
        ArrayNode array = array(root, kind);
        for (int i = 0; i < array.size(); i++) {
            if (code.equals(array.get(i).path("code").asString(null))) {
                array.remove(i);
                save(root);
                log.warn("【安全闸】删除 {} {}, 操作人: {}", kind, code, operator);
                return;
            }
        }
        throw new IllegalArgumentException("没有这一条: " + code);
    }

    // ------------------------------------------------------------------ 工具

    private ObjectNode load() {
        String json = configService.getConfigValue(ConfigKeyEnum.GATEWAY_SECURITY_GATE);
        try {
            JsonNode node = json == null ? null : JsonUtils.getMapper().readTree(json);
            if (node instanceof ObjectNode object) {
                return object;
            }
        } catch (Exception e) {
            log.warn("【安全闸】gateway_security_gate 解不开", e);
        }
        throw new IllegalArgumentException("网关安全闸配置（t_config.gateway_security_gate）解不开，先到系统参数里修好它");
    }

    private void save(ObjectNode root) {
        try {
            configService.updateValueByKey(ConfigKeyEnum.GATEWAY_SECURITY_GATE, JsonUtils.getMapper().writeValueAsString(root));
        } catch (BusinessException e) {
            // 保存前的校验（反例集）没过：消息原样给页面
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static ArrayNode array(ObjectNode root, Kind kind) {
        return root.get(kind.field) instanceof ArrayNode array ? array : root.putArray(kind.field);
    }

    private static ObjectNode find(ObjectNode root, Kind kind, String code) {
        for (JsonNode e : array(root, kind)) {
            if (code.equals(e.path("code").asString(null)) && e instanceof ObjectNode object) {
                return object;
            }
        }
        throw new IllegalArgumentException("没有这一条: " + code);
    }

    private static void requireNew(ObjectNode root, Kind kind, String code) {
        String normalized = normalizeCode(code);
        for (JsonNode e : array(root, kind)) {
            if (normalized.equals(e.path("code").asString(null))) {
                throw new IllegalArgumentException("编码已存在: " + normalized);
            }
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** 熔断集合里窗口内的不同 IP 数（ZSET，分数是毫秒时间） */
    private long distinct(String key, long windowSeconds, long nowMillis) {
        try {
            Long n = redis.opsForZSet().count(key, nowMillis - windowSeconds * 1000, Double.MAX_VALUE);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }
}
