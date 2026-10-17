package solvela.scriptengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.scriptengine.annotation.ScriptFunction;
import solvela.scriptengine.core.DefaultScriptEngine;
import solvela.scriptengine.core.QLExpressEvaluator;
import solvela.scriptengine.core.ScriptEngineProperties;
import solvela.scriptengine.domain.EngineFunctionMeta;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.handler.ToolCollectionHandler;
import solvela.scriptengine.handler.ToolDateTimeHandler;
import solvela.scriptengine.handler.ToolJsonHandler;
import solvela.scriptengine.handler.ToolStringHandler;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptEvaluator;
import solvela.scriptengine.spi.ScriptFunctionHandler;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code tool_} 域里时间、字符串、JSON 三组函数的行为固化。
 *
 * <p>重点不在「功能对不对」，在<b>三条容易静默出错的边界</b>：
 * <ol>
 *   <li>时间参数既可能是字符串也可能是 {@code LocalDateTime}，两种都得认 ——
 *       声明成 String 的话，传 LocalDateTime 会被 QL 静默转成 null；</li>
 *   <li>取不到的值要给默认值，不能是 null 一路往下走；</li>
 *   <li>格式/解析失败要报错，不能返回一个看起来正常的空值。</li>
 * </ol>
 */
public class ToolFunctionTest {

    private ScriptEngine scriptEngine;

    private ScriptEvaluator evaluator;

    @BeforeEach
    void setUp() {
        this.evaluator = new QLExpressEvaluator(new ScriptEngineProperties());
        this.scriptEngine = new DefaultScriptEngine(evaluator);
        bind(new ToolDateTimeHandler());
        bind(new ToolStringHandler());
        bind(new ToolJsonHandler());
        // tool_size 等集合函数也在 tool_ 域里，断言时会用到
        bind(new ToolCollectionHandler());
    }

    private Object run(String script) {
        return run(script, EngineContext.create());
    }

    private Object run(String script, EngineContext context) {
        return scriptEngine.evaluate(ExecutableScript.trusted("test/tool", script), context);
    }

    // =====================================================================
    // 时间
    // =====================================================================

    @Test
    @DisplayName("🔴 时间函数同时认字符串和场景里的 LocalDateTime —— 后者是真正会踩的那个")
    void time_functions_accept_both_text_and_local_date_time() {
        EngineContext context = EngineContext.create()
                .bind("eventTime", LocalDateTime.of(2026, 9, 6, 21, 30))
                .bind("birthday", LocalDate.of(1990, 1, 1));

        // 场景变量绑进来的是 LocalDateTime。函数若声明成 String，这里拿到的会是 null
        assertEquals(21, run("return tool_hourOfDay(eventTime);", context));
        assertEquals("2026-09-06", run("return tool_dateOf(eventTime);", context));
        assertEquals(7, run("return tool_dayOfWeek(eventTime);", context), "2026-09-06 是周日");
        assertEquals(Boolean.TRUE, run("return tool_isWeekend(eventTime);", context));
        assertEquals("1990-01-01", run("return tool_dateOf(birthday);", context));

        // 字符串形态同样认
        assertEquals(9, run("return tool_hourOfDay('2026-09-06 09:15:00');"));
        assertEquals("2026-09-06", run("return tool_dateOf('2026-09-06');"));
    }

    @Test
    @DisplayName("认不出来的时间直接报错，并说清收到的是什么 —— 不静默当成现在")
    void unrecognized_time_is_reported_not_silently_defaulted() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> run("return tool_hourOfDay('昨天下午');"));
        assertTrue(e.getMessage().contains("认不出来"), "实际: " + e.getMessage());
    }

    @Test
    @DisplayName("不传参数就是现在；多传直接报错")
    void optional_time_argument_defaults_to_now() {
        Object hour = run("return tool_hourOfDay();");
        assertTrue((Integer) hour >= 0 && (Integer) hour <= 23);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> run("return tool_hourOfDay('2026-09-06', '2026-09-07');"));
        assertTrue(e.getMessage().contains("最多只收一个"), "实际: " + e.getMessage());
    }

    @Test
    @DisplayName("daysBetween / daysSince / secondsBetween 按预期算差值")
    void time_differences() {
        assertEquals(2L, run("return tool_daysBetween('2026-09-06', '2026-09-08');"));
        assertEquals(-2L, run("return tool_daysBetween('2026-09-08', '2026-09-06');"));
        assertEquals(3600L, run("return tool_secondsBetween('2026-09-06 10:00:00', '2026-09-06 11:00:00');"));

        // 注册天数这类判据
        EngineContext context = EngineContext.create().bind("registerTime", LocalDateTime.now().minusDays(3));
        assertEquals(3L, run("return tool_daysSince(registerTime);", context));
    }

    @Test
    @DisplayName("🔴 nowBetween 支持跨零点的时段，否则运营只能写两个条件或起来（几乎必错一边）")
    void now_between_supports_windows_crossing_midnight() {
        // 造两个一定跨零点的窗口（起点晚于终点），断言不受「现在几点」影响
        DateTimeFormatter hourMinute = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime now = LocalTime.now();
        String justBefore = now.minusMinutes(1).format(hourMinute);
        String evenEarlier = now.minusMinutes(2).format(hourMinute);
        String justAfter = now.plusMinutes(1).format(hourMinute);

        // [一分钟前, 两分钟前) —— 绕过零点覆盖了几乎一整天，含此刻
        assertEquals(Boolean.TRUE, run("return tool_nowBetween('" + justBefore + "', '" + evenEarlier + "');"));
        // [一分钟后, 此刻) —— 同样绕过零点，唯独把此刻这一分钟挖掉了
        assertEquals(Boolean.FALSE, run("return tool_nowBetween('" + justAfter + "', '"
                + now.format(hourMinute) + "');"));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> run("return tool_nowBetween('09:00', '09:00');"));
        assertTrue(e.getMessage().contains("空区间"), "实际: " + e.getMessage());

        RuntimeException bad = assertThrows(RuntimeException.class,
                () -> run("return tool_nowBetween('9点', '12:00');"));
        assertTrue(bad.getMessage().contains("HH:mm"), "实际: " + bad.getMessage());
    }

    @Test
    @DisplayName("format 的结果能再喂回时间函数；格式串写错直接报错")
    void format_and_plus_days_round_trip() {
        assertEquals("2026-09-06 09:15", run("return tool_format('2026-09-06 09:15:00', 'yyyy-MM-dd HH:mm');"));
        assertEquals("2026-09-09", run("return tool_dateOf(tool_plusDays('2026-09-06 09:15:00', 3));"));
        assertEquals("2026-09-06 12:15:00",
                run("return tool_format(tool_plusHours('2026-09-06 09:15:00', 3), 'yyyy-MM-dd HH:mm:ss');"));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> run("return tool_format('2026-09-06', 'yyyy-QQQQQQ');"));
        assertTrue(e.getMessage().contains("不合法"), "实际: " + e.getMessage());
    }

    // =====================================================================
    // 字符串
    // =====================================================================

    @Test
    @DisplayName("🔴 字符串函数收 Object：传数字/时间进来不会静默变 null")
    void string_functions_accept_non_string_values() {
        EngineContext context = EngineContext.create().bind("memberId", 8848L);

        assertEquals("8848", run("return tool_str(memberId);", context));
        assertEquals(Boolean.FALSE, run("return tool_isBlank(memberId);", context));
        assertEquals("88", run("return tool_substring(memberId, 2);", context));
        // null 拼串在 QL 里会拼出 "key:null"，tool_str 把它变成空
        assertEquals("key:", run("return 'key:' + tool_str(null);"));
    }

    @Test
    @DisplayName("tool_num 把前端传来的参数安全转数字，转不了就用默认值")
    void num_falls_back_to_default() {
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) run("return tool_num('3', 1);")));
        assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) run("return tool_num('三', 1);")));
        assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) run("return tool_num(null, 1);")));
        // 转出来的是数字，可以直接参与比较
        assertEquals(Boolean.TRUE, run("return tool_num('10', 0) > 3;"));
    }

    @Test
    @DisplayName("split 是纯文本分隔不是正则，末尾空段也保留")
    void split_is_literal_and_keeps_trailing_empty() {
        assertEquals(List.of("a", "b", "c"), run("return tool_split('a|b|c', '|');"));
        assertEquals(3, run("return tool_size(tool_split('a,b,', ','));"),
                "末尾空段要保留，否则按位置取值的脚本会静默取错");
    }

    @Test
    @DisplayName("mask 按码点打码，用于日志脱敏")
    void mask_hides_the_middle() {
        assertEquals("138****8000", run("return tool_mask('13800008000', 3, 7);"));
    }

    @Test
    @DisplayName("trim / upper / lower / startsWith / defaultIfBlank")
    void assorted_string_helpers() {
        assertEquals("abc", run("return tool_trim('  abc  ');"));
        assertEquals("ABC", run("return tool_upper('abc');"));
        assertEquals(Boolean.TRUE, run("return tool_startsWith('DRAW_POOL_A', 'DRAW_');"));
        assertEquals("NORMAL", run("return tool_defaultIfBlank('   ', 'NORMAL');"));
        assertEquals(Boolean.TRUE, run("return tool_equalsIgnoreCase('Draw', 'DRAW');"));
    }

    // =====================================================================
    // JSON
    // =====================================================================

    @Test
    @DisplayName("🔴 jsonGet 对 map 和 JSON 串一视同仁：payload 是 map，缓存里取出来的是串")
    void json_get_works_on_both_maps_and_text() {
        EngineContext context = EngineContext.create()
                .bind("payload", Map.of("order", Map.of("amount", 128, "items", List.of(Map.of("skuId", "S1")))));

        assertEquals(128, run("return tool_jsonGet(payload, 'order.amount', 0);", context));
        assertEquals("S1", run("return tool_jsonGet(payload, 'order.items.0.skuId', '');", context));
        assertEquals(128, run("return tool_jsonGet('{\"order\":{\"amount\":128}}', 'order.amount', 0);"));
    }

    @Test
    @DisplayName("🔴 jsonGet 断在哪一层都返回默认值，不抛也不给 null")
    void json_get_returns_default_at_any_broken_level() {
        EngineContext context = EngineContext.create().bind("payload", Map.of("order", Map.of("amount", 128)));

        assertEquals(0, run("return tool_jsonGet(payload, 'order.discount', 0);", context));
        assertEquals(0, run("return tool_jsonGet(payload, 'nothing.here.at.all', 0);", context));
        assertEquals(0, run("return tool_jsonGet(null, 'order.amount', 0);"));
        assertEquals(0, run("return tool_jsonGet('这不是 json', 'order.amount', 0);"));
    }

    @Test
    @DisplayName("toJson 出来的串能被 parseJson 读回去")
    void to_json_and_parse_json_round_trip() {
        assertEquals("{\"a\":1}", run("return tool_toJson({\"a\": 1});"));
        assertEquals(1, run("return tool_jsonGet(tool_parseJson(tool_toJson({\"a\": 1})), 'a', 0);"));
        // 已经是结构的原样返回，脚本不必先判断手上是串还是 map
        assertEquals(Map.of("a", 1), run("return tool_parseJson({\"a\": 1});"));
    }

    @Test
    @DisplayName("🔴 parseJson 解析失败直接报错，不返回 null 让脚本静默走错分支")
    void parse_json_fails_loudly() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> run("return tool_parseJson('{坏的');"));
        assertTrue(e.getMessage().contains("解析失败"), "实际: " + e.getMessage());
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
