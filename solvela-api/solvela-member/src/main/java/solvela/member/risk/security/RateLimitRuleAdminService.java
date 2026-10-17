package solvela.member.risk.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import solvela.base.json.JsonUtils;
import solvela.member.RateLimitRule;
import solvela.member.risk.dao.RateLimitRuleDao;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.security.SecurityKeys;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台「限流规则」页（网关方案 §6.1、§6.2、§6.5）：场景 × 维度的阈值与档位，以及按接口的覆盖行。
 *
 * <h3>改完立即发布</h3>
 * 每一次保存都重发网关快照（{@link GatewaySnapshotPublisher#publishQuietly}），15 秒内各网关实例重载；
 * 页头的「各实例已加载版本」让人看得见生效没有（§6.5），不用猜。
 *
 * <h3>保存时的几条校验</h3>
 * <ul>
 *   <li>{@code burst ≤ 3 分钟 ≤ 5 分钟 ≤ 10 分钟}：短窗口的阈值比长窗口还大，长窗口就永远先触发 —— 短窗口形同虚设；</li>
 *   <li>三个窗口<b>亚线性</b>（每分钟的允许量随窗口变长而变小）：不然长窗口永远不会先触发，配了等于没配；</li>
 *   <li>接口覆盖行的接口必须在网关上报的路由清单里（不许手填）：一个 {@code {orderNo}} 写成 {@code {id}} 的行永远匹配不上，
 *       而它在页面上看起来好好的；</li>
 *   <li>场景行不能删（每个场景 × 维度都要有，缺了网关退回内置默认值），只能改；接口行可增可删。</li>
 * </ul>
 *
 * @Date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitRuleAdminService {

    /** 1-场景, 2-接口 */
    public static final int TARGET_SCENE = 1, TARGET_ENDPOINT = 2;

    private static final Set<String> LONG_WINDOWS = Set.of("1h", "1d");

    private final RateLimitRuleDao ruleDao;

    private final GatewaySnapshotPublisher snapshotPublisher;

    private final StringRedisTemplate redis;

    /** 一行要改成的样子 */
    public record RuleValues(int burst, int limit3m, int limit5m, int limit10m, String longWindow, Integer limitLong,
                             int mode) {
    }

    /** 网关上报的一个接口 */
    public record Route(String endpointKey, String scene) {
    }

    /** 一个网关实例此刻加载的规则版本 */
    public record LoadedVersion(String instance, String version, String source, Long reportedAt) {
    }

    /** 页头：已发布的版本，与各实例已加载的版本 */
    public record Versions(String published, List<LoadedVersion> instances) {
    }

    public List<RateLimitRule> list() {
        return ruleDao.selectList(new LambdaQueryWrapper<RateLimitRule>()
                .orderByAsc(RateLimitRule::getTargetType).orderByAsc(RateLimitRule::getTarget)
                .orderByAsc(RateLimitRule::getDimension));
    }

    public RateLimitRule update(long id, RuleValues v, String operator) {
        RateLimitRule row = ruleDao.selectById(id);
        if (row == null) {
            throw new IllegalArgumentException("没有这条限流规则: " + id);
        }
        validate(v);
        apply(row, v);
        // 🔴 不用 updateById：它跳过 null 字段，「清掉长窗口」就存不进去 —— 页面上显示已清，库里还在
        ruleDao.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RateLimitRule>()
                .eq(RateLimitRule::getId, id)
                .set(RateLimitRule::getBurst, row.getBurst())
                .set(RateLimitRule::getLimit3m, row.getLimit3m())
                .set(RateLimitRule::getLimit5m, row.getLimit5m())
                .set(RateLimitRule::getLimit10m, row.getLimit10m())
                .set(RateLimitRule::getLongWindow, row.getLongWindow())
                .set(RateLimitRule::getLimitLong, row.getLimitLong())
                .set(RateLimitRule::getMode, row.getMode()));
        snapshotPublisher.publishQuietly("修改限流规则");
        log.warn("【限流规则】修改, id: {}, 目标: {}, 维度: {}, 值: {}, 操作人: {}", id, row.getTarget(), row.getDimension(), v, operator);
        return row;
    }

    /** 加一条按接口的覆盖行。接口必须在网关上报的路由清单里 */
    public RateLimitRule addEndpoint(String endpointKey, int dimension, RuleValues v, String operator) {
        if (dimension < 0 || dimension > 4) {
            throw new IllegalArgumentException("维度只能是 0-全局, 1-IP, 2-账号, 3-设备, 4-IP段");
        }
        if (routes().stream().noneMatch(r -> r.endpointKey().equals(endpointKey))) {
            throw new IllegalArgumentException("网关上报的路由清单里没有这个接口: " + endpointKey
                    + " —— 只能从下拉框里选，手填的接口键对不上就永远不生效");
        }
        validate(v);
        boolean exists = ruleDao.selectCount(new LambdaQueryWrapper<RateLimitRule>()
                .eq(RateLimitRule::getTargetType, TARGET_ENDPOINT).eq(RateLimitRule::getTarget, endpointKey)
                .eq(RateLimitRule::getDimension, dimension)) > 0;
        if (exists) {
            throw new IllegalArgumentException("这个接口的这个维度已经有覆盖行了，改那一行");
        }
        RateLimitRule row = new RateLimitRule();
        row.setTargetType(TARGET_ENDPOINT);
        row.setTarget(endpointKey);
        row.setDimension(dimension);
        apply(row, v);
        row.setDescription("后台按接口覆盖（" + operator + "）");
        ruleDao.insert(row);
        snapshotPublisher.publishQuietly("新增接口限流覆盖");
        log.warn("【限流规则】新增接口覆盖, 接口: {}, 维度: {}, 值: {}, 操作人: {}", endpointKey, dimension, v, operator);
        return row;
    }

    /** 删一条接口覆盖行。场景行不能删 */
    public void deleteEndpoint(long id, String operator) {
        RateLimitRule row = ruleDao.selectById(id);
        if (row == null) {
            return;
        }
        if (row.getTargetType() != TARGET_ENDPOINT) {
            throw new IllegalArgumentException("场景行不能删：每个场景 × 维度都要有一行，不想要就把档位改成关闭");
        }
        ruleDao.deleteById(id);
        snapshotPublisher.publishQuietly("删除接口限流覆盖");
        log.warn("【限流规则】删除接口覆盖, 接口: {}, 维度: {}, 操作人: {}", row.getTarget(), row.getDimension(), operator);
    }

    /** 网关上报的路由清单（{@link SecurityKeys#ROUTES}）。网关还没起来过时为空 */
    public List<Route> routes() {
        String raw = redis.opsForValue().get(SecurityKeys.ROUTES);
        if (raw == null) {
            return List.of();
        }
        try {
            JsonNode root = JsonUtils.getMapper().readTree(raw);
            JsonNode scenes = root.path("scenes");
            List<Route> routes = new ArrayList<>();
            for (JsonNode r : root.path("routes")) {
                String key = r.asString();
                routes.add(new Route(key, scenes.path(key).asString(null)));
            }
            return routes;
        } catch (Exception e) {
            log.warn("【限流规则】路由清单解不开", e);
            return List.of();
        }
    }

    /** 已发布的版本，与各网关实例已加载的版本（实例停了，它的那一条一分钟内自然消失） */
    public Versions versions() {
        Object published = redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_VERSION);
        Map<String, LoadedVersion> instances = new LinkedHashMap<>();
        Set<String> keys = redis.keys(SecurityKeys.LOADED_VERSION_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                String raw = redis.opsForValue().get(key);
                if (raw == null) {
                    continue;
                }
                String instance = key.substring(SecurityKeys.LOADED_VERSION_PREFIX.length());
                try {
                    JsonNode n = JsonUtils.getMapper().readTree(raw);
                    instances.put(instance, new LoadedVersion(instance, n.path("version").asString(null),
                            n.path("source").asString(null),
                            n.path("reportedAt").canConvertToLong() ? n.path("reportedAt").asLong() : null));
                } catch (Exception e) {
                    instances.put(instance, new LoadedVersion(instance, null, null, null));
                }
            }
        }
        return new Versions(published == null ? null : published.toString(), List.copyOf(instances.values()));
    }

    static void validate(RuleValues v) {
        if (v.mode() < 0 || v.mode() > 2) {
            throw new IllegalArgumentException("档位只能是 0-关闭, 1-陪跑, 2-生效");
        }
        if (v.burst() < 1 || v.limit3m() < 1 || v.limit5m() < 1 || v.limit10m() < 1) {
            throw new IllegalArgumentException("阈值至少是 1");
        }
        if (!(v.burst() <= v.limit3m() && v.limit3m() <= v.limit5m() && v.limit5m() <= v.limit10m())) {
            throw new IllegalArgumentException("要满足 突发 ≤ 3 分钟 ≤ 5 分钟 ≤ 10 分钟：短窗口比长窗口还宽，长窗口就永远先触发");
        }
        // 亚线性：每分钟的允许量随窗口变长而变小。5 分钟的量 ≥ 3 分钟的 5/3 倍，5 分钟窗口就永远不会先触发
        if (v.limit5m() * 3L >= v.limit3m() * 5L || v.limit10m() >= v.limit5m() * 2L) {
            throw new IllegalArgumentException("三个窗口要亚线性：5 分钟要小于 3 分钟的 5/3 倍、10 分钟要小于 5 分钟的 2 倍"
                    + " —— 否则长窗口永远不会先触发，配了等于没配");
        }
        boolean hasLong = v.longWindow() != null && !v.longWindow().isBlank();
        if (hasLong != (v.limitLong() != null)) {
            throw new IllegalArgumentException("长窗口与长窗口阈值要么都填、要么都不填");
        }
        // 长窗口的阈值可以比 10 分钟的还小：它抓的是「每 10 分钟都卡在阈值下、慢慢滴」的脚本，
        // 迁过来的几条本来就是这样（注册：10 分钟 5 次，但一天只许 2 次）
        if (hasLong && (!LONG_WINDOWS.contains(v.longWindow()) || v.limitLong() < 1)) {
            throw new IllegalArgumentException("长窗口只能是 1h 或 1d，阈值至少是 1");
        }
    }

    private static void apply(RateLimitRule row, RuleValues v) {
        row.setBurst(v.burst());
        row.setLimit3m(v.limit3m());
        row.setLimit5m(v.limit5m());
        row.setLimit10m(v.limit10m());
        boolean hasLong = v.longWindow() != null && !v.longWindow().isBlank();
        row.setLongWindow(hasLong ? v.longWindow() : null);
        row.setLimitLong(hasLong ? v.limitLong() : null);
        row.setMode(v.mode());
    }
}
