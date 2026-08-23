package sa.scriptengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sa.base.common.exception.BusinessException;
import sa.base.common.exception.EngineScriptException;
import sa.scriptengine.annotation.ScriptFunction;
import sa.scriptengine.core.DefaultScriptEngine;
import sa.scriptengine.core.EngineFunctionRegistry;
import sa.scriptengine.core.QLExpressEvaluator;
import sa.scriptengine.core.ScriptEngineProperties;
import sa.scriptengine.domain.EngineFunctionMeta;
import sa.scriptengine.domain.ExecutableScript;
import sa.scriptengine.domain.ScriptFunctionDocDTO;
import sa.scriptengine.spi.EngineContext;
import sa.scriptengine.spi.ScriptDomain;
import sa.scriptengine.spi.ScriptEngine;
import sa.scriptengine.spi.ScriptEvaluator;
import sa.scriptengine.spi.ScriptFunctionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本引擎的行为固化测试。
 *
 * <p>钉住的是六条<b>契约</b>，每一条对应一个真实踩过或差点踩到的坑：
 * <ol>
 *   <li>bind 进去的变量脚本里取得到 —— 重构前走错了 execute 重载，这条是不成立的；</li>
 *   <li>函数名带域前缀，跨域不可能撞名；</li>
 *   <li>首参 EngineContext 会被自动注入，不靠 ThreadLocal；</li>
 *   <li>Java 函数里抛的业务异常原样冒上来，不被反射层包住；</li>
 *   <li>隔离策略下脚本碰不到未经白名单暴露的 Java 成员；</li>
 *   <li>语法错误带得出行号。</li>
 * </ol>
 * 这些都不依赖 Spring 容器，纯 POJO 装配即可复现。
 */
public class ScriptEngineTest {

    private ScriptEngine scriptEngine;

    private ScriptEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ScriptEngineProperties properties = new ScriptEngineProperties();
        this.evaluator = new QLExpressEvaluator(properties);
        this.scriptEngine = new DefaultScriptEngine(evaluator);
        bind(new MemberTestHandler());
    }

    // =====================================================================
    // 1. 变量绑定
    // =====================================================================

    @Test
    @DisplayName("bind 进上下文的变量，脚本里能按变量名直接取到")
    void bound_variables_are_visible_to_script() {
        EngineContext context = EngineContext.create()
                .bind("streakDays", 7)
                .bind("memberLevel", "GOLD");

        Object result = scriptEngine.evaluate(
                ExecutableScript.of("test/streak", "return streakDays >= 7 && memberLevel == \"GOLD\";"),
                context);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    @DisplayName("脚本内部定义的变量不会回写宿主上下文")
    void script_local_variables_do_not_pollute_host_context() {
        EngineContext context = EngineContext.create().bind("base", 10);

        scriptEngine.evaluate(ExecutableScript.of("test/pollute", "temp = base * 2; return temp;"), context);

        assertTrue(context.getVariables().containsKey("base"));
        assertTrue(!context.getVariables().containsKey("temp"), "脚本的局部变量 temp 不该出现在宿主上下文里");
    }

    // =====================================================================
    // 2. 域前缀
    // =====================================================================

    @Test
    @DisplayName("函数按 domain() 自动加前缀，脚本里用的是 member_ 开头的全限定名")
    void functions_are_registered_with_domain_prefix() {
        Object result = scriptEngine.evaluate(
                ExecutableScript.of("test/level", "return member_levelOf(100);"),
                EngineContext.create());

        assertEquals("LV100", result);
    }

    @Test
    @DisplayName("同名函数重复注册直接抛异常，不静默覆盖")
    void duplicated_function_name_fails_fast() {
        EngineFunctionRegistry registry = new EngineFunctionRegistry();
        MemberTestHandler handler = new MemberTestHandler();
        registry.register(metaOf(handler, "levelOf", Integer.class));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.register(metaOf(handler, "levelOf", Integer.class)));
        assertTrue(e.getMessage().contains("member_levelOf"));
    }

    @Test
    @DisplayName("导出的函数文档带域分组与调用签名，前端按这两个字段渲染")
    void exported_docs_carry_domain_and_signature() {
        EngineFunctionRegistry registry = new EngineFunctionRegistry();
        MemberTestHandler handler = new MemberTestHandler();
        registry.register(metaOf(handler, "levelOf", Integer.class));
        registry.register(metaOf(handler, "currentMemberId", EngineContext.class));

        List<ScriptFunctionDocDTO> docs = registry.exportDocs();
        ScriptFunctionDocDTO levelOf = docs.stream()
                .filter(doc -> "member_levelOf".equals(doc.getFunctionName()))
                .findFirst().orElseThrow();

        assertEquals("MEMBER", levelOf.getDomain());
        assertEquals("会员域", levelOf.getDomainTitle());
        assertEquals("member_levelOf(point)", levelOf.getSignature());

        // 注入的 EngineContext 不属于脚本侧参数，不能出现在文档里
        ScriptFunctionDocDTO injected = docs.stream()
                .filter(doc -> "member_currentMemberId".equals(doc.getFunctionName()))
                .findFirst().orElseThrow();
        assertTrue(injected.getParams().isEmpty(), "自动注入的 EngineContext 不该出现在文档参数里");
        assertEquals("member_currentMemberId()", injected.getSignature());
    }

    // =====================================================================
    // 3. 上下文注入
    // =====================================================================

    @Test
    @DisplayName("首参声明为 EngineContext 的函数会被自动注入上下文，脚本侧不传这个参数")
    void engine_context_is_injected_into_first_parameter() {
        EngineContext context = EngineContext.create().bind("memberId", 8848L);

        Object result = scriptEngine.evaluate(
                ExecutableScript.of("test/inject", "return member_currentMemberId();"),
                context);

        assertEquals(8848L, result);
    }

    // =====================================================================
    // 4. 异常穿透
    // =====================================================================

    @Test
    @DisplayName("Java 函数抛出的业务异常原样冒上来，不被反射层包成 InvocationTargetException")
    void business_exception_from_java_function_propagates_as_is() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ExecutableScript.of("test/boom", "return member_boom();"),
                EngineContext.create()));

        assertEquals("会员不存在", e.getMessage());
    }

    // =====================================================================
    // 5. 安全隔离
    // =====================================================================

    @Test
    @DisplayName("默认隔离策略下，脚本无法反射调用未经白名单暴露的 Java 成员")
    void isolation_strategy_blocks_arbitrary_java_access() {
        EngineContext context = EngineContext.create().bind("text", "hello");

        // getClass() 没有被 @ScriptFunction 暴露过，隔离策略必须拦住
        assertThrows(RuntimeException.class, () -> scriptEngine.evaluate(
                ExecutableScript.of("test/escape", "return text.getClass().getName();"),
                context));
    }

    // =====================================================================
    // 6. 语法报错
    // =====================================================================

    @Test
    @DisplayName("语法错误在 check 阶段就能捕获，且带得出行号")
    void syntax_error_is_reported_with_line_number() {
        EngineScriptException e = assertThrows(EngineScriptException.class, () -> scriptEngine.check(
                ExecutableScript.ofAdHoc("test/broken", "a = 1;\nreturn (a + ;")));

        assertTrue(e.getScriptErrorDetail().contains("行"),
                "报错详情应带行号，实际为: " + e.getScriptErrorDetail());
    }

    @Test
    @DisplayName("返回值类型不匹配时抛出可读的业务异常")
    void return_type_mismatch_is_rejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ExecutableScript.of("test/type", "return \"not a number\";"),
                EngineContext.create(), Integer.class));

        assertInstanceOf(BusinessException.class, e);
        assertTrue(e.getMessage().contains("类型不匹配"));
    }

    // =====================================================================
    // 测试用桩
    // =====================================================================

    /** 把 handler 上所有 @ScriptFunction 方法绑进 evaluator，等价于启动期 EngineFunctionScanner 做的事 */
    private void bind(ScriptFunctionHandler handler) {
        for (Method method : handler.getClass().getDeclaredMethods()) {
            ScriptFunction annotation = method.getAnnotation(ScriptFunction.class);
            if (annotation != null) {
                evaluator.registerFunction(metaOf(handler, method.getName(), method.getParameterTypes()));
            }
        }
    }

    private EngineFunctionMeta metaOf(ScriptFunctionHandler handler, String methodName, Class<?>... paramTypes) {
        try {
            Method method = handler.getClass().getMethod(methodName, paramTypes);
            ScriptFunction annotation = method.getAnnotation(ScriptFunction.class);
            boolean injectContext = paramTypes.length > 0 && EngineContext.class.isAssignableFrom(paramTypes[0]);
            return EngineFunctionMeta.builder()
                    .domain(handler.domain())
                    .functionName(handler.domain().qualify(annotation.name()))
                    .simpleName(annotation.name())
                    .targetBean(handler)
                    .method(method)
                    .injectContext(injectContext)
                    .description(annotation.description())
                    .returnType(method.getReturnType().getSimpleName())
                    .params(Arrays.stream(method.getParameters())
                            .skip(injectContext ? 1 : 0)
                            .map(p -> p.getType().getSimpleName() + " " + p.getName())
                            .collect(Collectors.toList()))
                    .build();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static class MemberTestHandler implements ScriptFunctionHandler {

        @Override
        public ScriptDomain domain() {
            return ScriptDomain.MEMBER;
        }

        @ScriptFunction(name = "levelOf", description = "测试用：拼一个等级串")
        public String levelOf(Integer point) {
            return "LV" + point;
        }

        @ScriptFunction(name = "currentMemberId", description = "测试用：从注入的上下文里取会员ID")
        public Long currentMemberId(EngineContext context) {
            return context.getAs("memberId", Long.class);
        }

        @ScriptFunction(name = "boom", description = "测试用：抛业务异常")
        public String boom() {
            throw new BusinessException("会员不存在");
        }
    }
}
