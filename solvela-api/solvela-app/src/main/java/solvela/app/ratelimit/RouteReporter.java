package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import solvela.security.SecurityKeys;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 启动时把本进程的<b>路由清单</b>上报到 Redis（{@link SecurityKeys#ROUTES}）。
 *
 * <h3>后台为什么需要它</h3>
 * 后台可以给单个接口覆盖限流阈值（网关方案 §6.1 的接口行）。接口键要和网关这边的路由<b>逐字一致</b>，
 * 手填的话，一个 {@code {orderNo}} 写成 {@code {id}} 的接口行就永远匹配不上 —— 而那一行在页面上看起来好好的。
 * 所以后台只允许从这份清单里选，并且把清单里已经没有的接口行标红。
 *
 * <p>接口键 = {@code METHOD 模板路径}（如 {@code POST /activity/{activityCode}/draw}），
 * 用的是 Spring 匹配时用的那个模板，不是带了具体值的 URL。
 *
 * <p>⚠️ 滚动发版时新旧实例各写一次，后写的覆盖先写的。路由只在发版时变，新版本的实例全起来之后清单就稳定了。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
public class RouteReporter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final StringRedisTemplate redis;

    private final RequestMappingHandlerMapping handlerMapping;

    private final RuleSnapshotHolder snapshotHolder;

    /** 显式写构造器：Lombok 默认不把 {@code @Qualifier} 抄到参数上，而文档组件会再注册别的映射器 */
    public RouteReporter(StringRedisTemplate redis,
                         @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                         RuleSnapshotHolder snapshotHolder) {
        this.redis = redis;
        this.handlerMapping = handlerMapping;
        this.snapshotHolder = snapshotHolder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        try {
            List<String> routes = routeKeys();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("instance", snapshotHolder.instance());
            payload.put("reportedAt", System.currentTimeMillis());
            payload.put("routes", routes);
            // 每个接口归哪个场景：后台的接口覆盖下拉框里，每个接口旁边要显示它（§6.5）
            payload.put("scenes", routeScenes());
            redis.opsForValue().set(SecurityKeys.ROUTES, MAPPER.writeValueAsString(payload));
            log.info("【网关路由】已上报 {} 个接口", routes.size());
        } catch (Exception e) {
            // 上报失败只影响后台的下拉框与失效标红，不影响限流本身
            log.error("【网关路由】上报失败，后台接口覆盖暂时选不到接口", e);
        }
    }

    /** 接口键 → 场景名。没标 {@code @RateLimit} 的接口不在里面（单测保证没有这种接口） */
    Map<String, String> routeScenes() {
        Map<String, String> scenes = new TreeMap<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            RateLimit rateLimit = method.getMethodAnnotation(RateLimit.class);
            if (rateLimit == null) {
                return;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (methods.isEmpty()) {
                    scenes.put("ANY " + pattern, rateLimit.value().name());
                } else {
                    methods.forEach(m -> scenes.put(m.name() + " " + pattern, rateLimit.value().name()));
                }
            }
        });
        return scenes;
    }

    /** 全部接口键，排好序。一个映射声明了多个方法或多个路径时展开成多条 */
    List<String> routeKeys() {
        Set<String> keys = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (methods.isEmpty()) {
                    keys.add("ANY " + pattern);
                } else {
                    methods.forEach(m -> keys.add(m.name() + " " + pattern));
                }
            }
        }
        return List.copyOf(keys);
    }
}
