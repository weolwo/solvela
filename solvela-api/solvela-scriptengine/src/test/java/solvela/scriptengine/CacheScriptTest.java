package solvela.scriptengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import solvela.base.module.redis.RedisService;
import solvela.scriptengine.annotation.ScriptFunction;
import solvela.scriptengine.core.DefaultScriptEngine;
import solvela.scriptengine.core.QLExpressEvaluator;
import solvela.scriptengine.core.ScriptEngineProperties;
import solvela.scriptengine.domain.EngineFunctionMeta;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.handler.CacheScriptHandler;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptEvaluator;
import solvela.scriptengine.spi.ScriptFunctionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code cache_} 域的行为固化测试。
 *
 * <p>钉的都是「护栏」而不是「功能」：键名前缀脚本改不掉、TTL 必填且有上限、
 * 键名不放行模式匹配字符、在线试跑碰不到线上脚本的键。
 * 功能本身（INCR/GET/DEL）是 {@code RedisService} 的事，这里只验参数拼对了没有。
 */
public class CacheScriptTest {

    private ScriptEngine scriptEngine;

    private ScriptEvaluator evaluator;

    private RedisService redisService;

    @BeforeEach
    void setUp() {
        this.redisService = mock(RedisService.class);
        // 真实实现是「项目名:环境:前缀+key」，这里用同样的形状，方便断言拼装结果
        when(redisService.generateRedisKey(anyString(), anyString()))
                .thenAnswer(call -> "solvela:test:" + call.getArgument(0) + call.getArgument(1));

        this.evaluator = new QLExpressEvaluator(new ScriptEngineProperties());
        this.scriptEngine = new DefaultScriptEngine(evaluator);
        bind(new CacheScriptHandler(redisService));
    }

    private Object run(String scriptName, String script) {
        return scriptEngine.evaluate(ExecutableScript.trusted(scriptName, script), EngineContext.create());
    }

    // =====================================================================
    // 键名
    // =====================================================================

    @Test
    @DisplayName("🔴 实际的键带上了脚本编码，脚本只能决定最后一段")
    void key_is_namespaced_by_script_code() {
        when(redisService.increment(anyString(), anyLong())).thenReturn(1L);

        run("ACT_MIDAUTUMN", "return cache_incr('join:' + 8848, 86400);");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redisService).increment(key.capture(), anyLong());
        assertEquals("solvela:test:script:ACT_MIDAUTUMN:join:8848", key.getValue());
    }

    @Test
    @DisplayName("🔴 在线试跑用的是 online-test 这个脚本名，键和线上脚本天然分开")
    void online_test_cannot_touch_production_keys() {
        when(redisService.increment(anyString(), anyLong())).thenReturn(1L);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        run("ACT_MIDAUTUMN", "return cache_incr('join', 60);");
        scriptEngine.evaluate(ExecutableScript.untrusted("online-test", "return cache_incr('join', 60);"),
                EngineContext.create());

        verify(redisService, Mockito.times(2)).increment(keys.capture(), anyLong());
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1),
                "试跑要是和线上共用一个键，运营点几下试跑就能把真实用户的次数用光");
    }

    @Test
    @DisplayName("🔴 键名不放行 * 和空格：那是模式匹配的入口，且必须在碰 Redis 之前就拦下")
    void illegal_key_name_is_rejected_before_touching_redis() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> run("test/cache", "return cache_del('*');"));

        assertTrue(e.getMessage().contains("不合法"), "实际: " + e.getMessage());
        verify(redisService, never()).delete(anyString());
    }

    // =====================================================================
    // TTL
    // =====================================================================

    @Test
    @DisplayName("🔴 TTL 必须大于 0：不允许写永不过期的键")
    void ttl_must_be_positive() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> run("test/cache", "return cache_incr('join', 0);"));

        assertTrue(e.getMessage().contains("TTL"), "实际: " + e.getMessage());
        verify(redisService, never()).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("TTL 上限 30 天，超了直接报错而不是截断")
    void ttl_has_an_upper_bound() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> run("test/cache",
                "return cache_set('flag', 'Y', " + (CacheScriptHandler.MAX_TTL_SECONDS + 1) + ");"));

        assertTrue(e.getMessage().contains("上限"), "实际: " + e.getMessage());
    }

    // =====================================================================
    // 读写语义
    // =====================================================================

    @Test
    @DisplayName("cache_incr 返回累加后的值，所以「最多 3 次」写的是 > 3")
    void incr_returns_the_value_after_increment() {
        when(redisService.increment(anyString(), anyLong())).thenReturn(4L);

        Object result = run("test/cache", """
                times = cache_incr('join', 86400);
                return times > 3;
                """);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    @DisplayName("cache_count 在键不存在或值不是数字时返回 0，不抛")
    void count_falls_back_to_zero() {
        when(redisService.get(anyString())).thenReturn(null);
        assertEquals(0L, run("test/cache", "return cache_count('join');"));

        when(redisService.get(anyString())).thenReturn("不是数字");
        assertEquals(0L, run("test/cache", "return cache_count('join');"));

        when(redisService.get(anyString())).thenReturn("7");
        assertEquals(7L, run("test/cache", "return cache_count('join');"));
    }

    @Test
    @DisplayName("cache_set 的值有长度上限，超了报错")
    void set_value_length_is_capped() {
        String longValue = "x".repeat(600);
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> run("test/cache", "return cache_set('k', '" + longValue + "', 60);"));

        assertTrue(e.getMessage().contains("上限"), "实际: " + e.getMessage());
        verify(redisService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("cache_ttl 原样透出 -1 / -2，脚本要分得清「没设过期」和「键不存在」")
    void ttl_negative_values_are_passed_through() {
        when(redisService.getExpire(anyString())).thenReturn(-2L);
        assertEquals(-2L, run("test/cache", "return cache_ttl('join');"));
    }

    // ------------------------------------------------------------------

    private void bind(ScriptFunctionHandler handler) {
        for (Method method : handler.getClass().getDeclaredMethods()) {
            ScriptFunction annotation = method.getAnnotation(ScriptFunction.class);
            if (annotation == null) {
                continue;
            }
            boolean injectContext = method.getParameterCount() > 0
                    && EngineContext.class.isAssignableFrom(method.getParameterTypes()[0]);
            List<String> params = Arrays.stream(method.getParameters())
                    .skip(injectContext ? 1 : 0)
                    .map(p -> p.getType().getSimpleName() + " " + p.getName())
                    .collect(Collectors.toList());
            evaluator.registerFunction(EngineFunctionMeta.builder()
                    .domain(handler.domain())
                    .functionName(handler.domain().qualify(annotation.name()))
                    .simpleName(annotation.name())
                    .targetBean(handler)
                    .method(method)
                    .injectContext(injectContext)
                    .sideEffect(annotation.sideEffect())
                    .description(annotation.description())
                    .returnType(method.getReturnType().getSimpleName())
                    .params(params)
                    .build());
        }
    }
}
