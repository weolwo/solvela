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
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptEvaluator;
import solvela.scriptengine.spi.ScriptFunctionHandler;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集合在脚本里到底能怎么写 —— 一半是 QLExpress 4.x 的语义备忘，一半是 {@code tool_} 集合函数的行为。
 *
 * <p>前半部分（「QL 原生」那几个用例）钉的<b>不是我们的设计</b>，是 QL 在
 * {@code isolation} 安全策略下的既有行为。写下来是因为这些结论只能靠跑一遍得到，
 * 而错误的假设代价很高：以为 {@code list.add()} 能用、以为 {@code map.length} 有值，
 * 都会在运营写脚本时才暴露。
 */
public class ScriptCollectionTest {

    private ScriptEngine scriptEngine;

    private ScriptEvaluator evaluator;

    @BeforeEach
    void setUp() {
        this.evaluator = new QLExpressEvaluator(new ScriptEngineProperties());
        this.scriptEngine = new DefaultScriptEngine(evaluator);
        bind(new ToolCollectionHandler());
    }

    private Object run(String script) {
        return scriptEngine.evaluate(ExecutableScript.trusted("test/collection", script), EngineContext.create());
    }

    // =====================================================================
    // QL 原生：能用的
    // =====================================================================

    @Test
    @DisplayName("QL 语义备忘：有原生集合字面量，不需要 3.x 那种 NewList / NewMap")
    void native_literals_replace_new_list_and_new_map() {
        assertInstanceOf(List.class, run("return [1, 2, 3];"));
        assertInstanceOf(Map.class, run("return {\"a\": 1};"));
        // 空 map 是 {:} —— {} 会被当成空代码块
        assertEquals(0, ((Map<?, ?>) run("return {:};")).size());
    }

    @Test
    @DisplayName("QL 语义备忘：map 可以读写、list 可以按下标读，for-each 可用")
    void map_write_and_list_read_work_without_java_calls() {
        assertEquals(Map.of("a", 1), run("m = {:}; m['a'] = 1; return m;"));
        assertEquals(Map.of("a", 1), run("m = {:}; m.a = 1; return m;"));
        assertEquals(1, run("return [1, 2, 3][0];"));
        assertEquals(3, run("return [1, 2, 3].length;"));
        assertEquals(0, new BigDecimal("6").compareTo(
                (BigDecimal) run("r = 0; for (x : [1,2,3]) { r = r + x; } return r;")));
    }

    // =====================================================================
    // QL 原生：不能用的 —— 这几条正是 tool_ 集合函数存在的理由
    // =====================================================================

    @Test
    @DisplayName("🔴 隔离策略下 Java 方法调用全部失败：list.add / map.put / new HashMap 都不通")
    void java_calls_are_blocked_under_isolation() {
        assertThrows(RuntimeException.class, () -> run("l = [1,2]; l.add(3); return l;"));
        assertThrows(RuntimeException.class, () -> run("m = {:}; m.put('a', 1); return m;"));
        assertThrows(RuntimeException.class, () -> run("return new HashMap();"));
        assertThrows(RuntimeException.class, () -> run("return new ArrayList();"));
        assertThrows(RuntimeException.class, () -> run("l = [1,2]; return l.size();"));
    }

    @Test
    @DisplayName("🔴 脚本顶部 import 也救不了：import 只影响类名解析，不影响能不能调用")
    void import_does_not_lift_the_isolation_block() {
        // import 语句本身是合法的（且必须在文件最开头）
        assertEquals(1, run("import java.util.HashMap;\nreturn 1;"));
        // 但 import 完照样 new 不出来
        assertThrows(RuntimeException.class,
                () -> run("import java.util.HashMap;\nm = new HashMap(); return m;"));
        // 不在开头则是语法错误
        assertThrows(RuntimeException.class, () -> run("a = 1;\nimport java.util.HashMap;\nreturn a;"));
    }

    @Test
    @DisplayName("🔴 map 没有 length：m.length 是在取一个名叫 length 的键，静默返回 null")
    void map_length_is_a_key_lookup_not_a_size() {
        assertNull(run("m = {\"a\": 1}; return m.length;"),
                "这条是静默的：不报错，只是永远拿到 null。集合大小必须用 tool_size");
        assertEquals(1, run("m = {\"a\": 1}; return tool_size(m);"));
    }

    // =====================================================================
    // tool_ 集合函数
    // =====================================================================

    @Test
    @DisplayName("tool_listAdd 补上了 list.add：能把列表变长，并返回同一个列表")
    void list_add_makes_a_list_growable() {
        Object result = run("l = tool_listOf('a'); tool_listAdd(l, 'b'); return l;");
        assertEquals(List.of("a", "b"), result);

        // 连着写也成立
        assertEquals(List.of(1, 2), run("return tool_listAdd(tool_listAdd([], 1), 2);"));
    }

    @Test
    @DisplayName("tool_listOf / tool_mapOf 把返回值包一层，mapOf 参数不成对直接报错")
    void list_of_and_map_of() {
        assertEquals(List.of("A", "B"), run("return tool_listOf('A', 'B');"));
        assertEquals(Map.of("a", 1), run("return tool_mapOf('a', 1);"));

        RuntimeException e = assertThrows(RuntimeException.class, () -> run("return tool_mapOf('a', 1, 'b');"));
        assertTrue(e.getMessage().contains("成对"), "报错要说清是参数没成对。实际: " + e.getMessage());
    }

    @Test
    @DisplayName("🔴 tool_get 取不到时给默认值，而 params['x'] 取不到是 null")
    void get_returns_default_instead_of_null() {
        assertEquals("NORMAL", run("return tool_get({\"tier\": \"VIP\"}, 'other', 'NORMAL');"));
        assertEquals("VIP", run("return tool_get({\"tier\": \"VIP\"}, 'tier', 'NORMAL');"));
        // 越界与 null 集合都走默认值，不抛
        assertEquals("-", run("return tool_get([1,2], 9, '-');"));
        assertEquals("-", run("return tool_get(null, 'k', '-');"));
        // 对照组：原生写法在这里是 null，然后一路 null 下去
        assertNull(run("m = {\"tier\": \"VIP\"}; return m['other'];"));
    }

    @Test
    @DisplayName("🔴 tool_contains 按数值比数字：字面量的 1 是 Integer，算出来的 1 是 BigDecimal")
    void contains_compares_numbers_by_value_not_by_type() {
        // 0 + 1 在 precise 模式下是 BigDecimal，而 [1,2,3] 里的 1 是 Integer。
        // 按 equals 判是 false —— 写脚本的人没有任何办法预料到这件事
        assertEquals(Boolean.TRUE, run("return tool_contains([1,2,3], 0 + 1);"));
        assertEquals(Boolean.TRUE, run("return tool_contains({\"a\": 1}, 'a');"));
        assertEquals(Boolean.FALSE, run("return tool_contains([1,2,3], 9);"));
    }

    @Test
    @DisplayName("tool_size / tool_isEmpty / tool_join 覆盖 list、map、字符串与 null")
    void size_is_empty_and_join() {
        assertEquals(0, run("return tool_size(null);"));
        assertEquals(3, run("return tool_size('abc');"));
        assertEquals(2, run("return tool_size([1,2]);"));
        assertEquals(Boolean.TRUE, run("return tool_isEmpty([]);"));
        assertEquals("a-b", run("return tool_join(['a','b'], '-');"));
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
