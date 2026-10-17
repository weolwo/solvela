package solvela.app.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.security.SecurityKeys;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路由清单：接口键是 Spring 匹配用的模板（带 {@code {变量}}），不是具体 URL。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class RouteReporterTest {

    @Autowired
    private RouteReporter reporter;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("接口键是「方法 + 模板路径」，带路径变量的接口保留模板")
    void routeKeysUseTemplates() {
        List<String> routes = reporter.routeKeys();

        assertTrue(routes.contains("POST /auth/login"), routes.toString());
        assertTrue(routes.contains("POST /activity/{activityCode}/draw"),
                "后台接口覆盖要按模板匹配：" + routes);
        assertEquals(routes.stream().sorted().toList(), routes, "清单要排好序，后台下拉框才好找");
    }

    @Test
    @DisplayName("上报到 gw:routes，后台能读到")
    void reportsToRedis() throws Exception {
        reporter.report();

        String raw = redis.opsForValue().get(SecurityKeys.ROUTES);
        assertNotNull(raw);
        Map<?, ?> payload = JsonMapper.builder().build().readValue(raw, Map.class);
        assertTrue(((List<?>) payload.get("routes")).contains("POST /auth/login"));
    }
}
