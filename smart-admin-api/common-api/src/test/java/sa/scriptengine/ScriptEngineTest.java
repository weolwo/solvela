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
import sa.scriptengine.domain.ScriptSource;
import sa.scriptengine.spi.EngineContext;
import sa.scriptengine.spi.ScriptDomain;
import sa.scriptengine.spi.ScriptEngine;
import sa.scriptengine.spi.ScriptEvaluator;
import sa.scriptengine.spi.ScriptFunctionHandler;
import sa.scriptengine.spi.ScriptScene;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本引擎的行为固化测试。
 *
 * <p>钉住的是八组<b>契约</b>，每一组对应一个真实踩过或差点踩到的坑：
 * <ol>
 *   <li>bind 进去的变量脚本里取得到 —— 重构前走错了 execute 重载，这条是不成立的；</li>
 *   <li>函数名带域前缀，跨域不可能撞名；</li>
 *   <li>首参 EngineContext 会被自动注入，不靠 ThreadLocal；</li>
 *   <li><b>内部数据通道对脚本完全不可见</b>，但 Java 函数读得到；</li>
 *   <li>Java 函数里抛的业务异常原样冒上来，不被反射层包住；</li>
 *   <li>隔离策略下脚本碰不到未经白名单暴露的 Java 成员；</li>
 *   <li><b>脚本来源必须显式声明</b>，且只影响引擎内部优化、不影响执行语义；</li>
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
                ExecutableScript.trusted("test/streak", "return streakDays >= 7 && memberLevel == \"GOLD\";"),
                context);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    @DisplayName("脚本内部定义的变量不会回写宿主上下文")
    void script_local_variables_do_not_pollute_host_context() {
        EngineContext context = EngineContext.create().bind("base", 10);

        scriptEngine.evaluate(ExecutableScript.trusted("test/pollute", "temp = base * 2; return temp;"), context);

        assertTrue(context.getScriptVariables().containsKey("base"));
        assertTrue(!context.getScriptVariables().containsKey("temp"), "脚本的局部变量 temp 不该出现在宿主上下文里");
    }

    // =====================================================================
    // 2. 域前缀
    // =====================================================================

    @Test
    @DisplayName("函数按 domain() 自动加前缀，脚本里用的是 member_ 开头的全限定名")
    void functions_are_registered_with_domain_prefix() {
        Object result = scriptEngine.evaluate(
                ExecutableScript.trusted("test/level", "return member_levelOf(100);"),
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
                ExecutableScript.trusted("test/inject", "return member_currentMemberId();"),
                context);

        assertEquals(8848L, result);
    }

    @Test
    @DisplayName("内部数据通道对脚本完全不可见，但 Java 函数读得到")
    void internal_channel_is_invisible_to_script_but_readable_by_java() {
        EngineContext context = EngineContext.create()
                .bind("memberId", 8848L)
                .bindInternal("traceId", "trace-abc-123");

        // 脚本侧：直接引用 traceId 拿不到值
        Object fromScript = scriptEngine.evaluate(
                ExecutableScript.trusted("test/leak", "return traceId;"),
                context);
        assertNull(fromScript, "内部数据不该出现在脚本可见的变量表里");

        // Java 函数侧：通过注入的 EngineContext 读得到
        Object fromJava = scriptEngine.evaluate(
                ExecutableScript.trusted("test/trace", "return member_currentTraceId();"),
                context);
        assertEquals("trace-abc-123", fromJava);
    }

    @Test
    @DisplayName("getScriptVariables 是脚本可见性的唯一出口，不含内部数据")
    void script_variables_view_excludes_internal_channel() {
        EngineContext context = EngineContext.create()
                .bind("memberId", 1L)
                .bindInternal("operator", "admin");

        assertTrue(context.getScriptVariables().containsKey("memberId"));
        assertFalse(context.getScriptVariables().containsKey("operator"));
        assertEquals("admin", context.getInternal("operator", String.class));
    }

    @Test
    @DisplayName("两条通道互不串味：同名 key 各存各的")
    void two_channels_are_independent_for_the_same_key() {
        EngineContext context = EngineContext.create()
                .bind("scope", "script-side")
                .bindInternal("scope", "java-side");

        assertEquals("script-side", context.getAs("scope", String.class));
        assertEquals("java-side", context.getInternal("scope", String.class));
    }

    // =====================================================================
    // 4. 异常穿透
    // =====================================================================

    @Test
    @DisplayName("Java 函数抛出的业务异常原样冒上来，不被反射层包成 InvocationTargetException")
    void business_exception_from_java_function_propagates_as_is() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ExecutableScript.trusted("test/boom", "return member_boom();"),
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
                ExecutableScript.trusted("test/escape", "return text.getClass().getName();"),
                context));
    }

    // =====================================================================
    // 6. 脚本来源
    // =====================================================================

    @Test
    @DisplayName("脚本来源必须显式声明，两个工厂各自打上正确的来源")
    void script_source_must_be_explicit() {
        assertEquals(ScriptSource.TRUSTED, ExecutableScript.trusted("a", "return 1;").source());
        assertEquals(ScriptSource.UNTRUSTED, ExecutableScript.untrusted("b", "return 1;").source());

        // 来源为空直接拒绝构造，不给"忘了填"留后门
        assertThrows(IllegalArgumentException.class, () -> new ExecutableScript("c", "return 1;", null));
    }

    @Test
    @DisplayName("两种来源的脚本都能正常执行，来源只影响引擎内部优化决策")
    void both_sources_execute_identically() {
        EngineContext context = EngineContext.create().bind("x", 6);
        String content = "return x * 7;";

        assertEquals(scriptEngine.evaluate(ExecutableScript.trusted("t", content), context),
                scriptEngine.evaluate(ExecutableScript.untrusted("u", content), context));
    }

    // =====================================================================
    // 7. 场景契约
    // =====================================================================

    /** 造一份满足 POOL_ENTRY 契约的上下文 */
    private EngineContext poolEntryContext() {
        return EngineContext.create()
                .bind("memberId", 8848L)
                .bind("activityCode", "ACT_2026")
                .bind("poolCode", "VIP_POOL");
    }

    @Test
    @DisplayName("按场景执行：契约齐全时正常返回")
    void scene_evaluation_passes_when_contract_is_satisfied() {
        Boolean result = scriptEngine.evaluate(
                ScriptScene.POOL_ENTRY,
                ExecutableScript.trusted("draw/vip_entry", "return memberId > 0 && poolCode == \"VIP_POOL\";"),
                poolEntryContext(),
                Boolean.class);

        assertTrue(result);
    }

    @Test
    @DisplayName("缺必填变量在执行前就失败，报的是变量名而不是脚本里的空指针")
    void missing_required_variable_fails_before_execution() {
        EngineContext incomplete = EngineContext.create()
                .bind("memberId", 8848L);   // 少了 activityCode / poolCode

        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ScriptScene.POOL_ENTRY,
                ExecutableScript.trusted("draw/vip_entry", "return true;"),
                incomplete,
                Boolean.class));

        assertTrue(e.getMessage().contains("activityCode"),
                "报错要点名缺了哪个变量，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("必填变量类型不符直接拒绝，不做数值宽松转换")
    void required_variable_type_mismatch_is_rejected() {
        EngineContext wrongType = EngineContext.create()
                .bind("memberId", "8848")   // 契约要求 Long，这里给了 String
                .bind("activityCode", "ACT_2026")
                .bind("poolCode", "VIP_POOL");

        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ScriptScene.POOL_ENTRY,
                ExecutableScript.trusted("draw/vip_entry", "return true;"),
                wrongType,
                Boolean.class));

        assertTrue(e.getMessage().contains("memberId") && e.getMessage().contains("Long"));
    }

    @Test
    @DisplayName("可选变量缺失不影响执行")
    void optional_variable_may_be_absent() {
        // TASK_RULE 的 isNewMember / target 是可选的，不绑也应该能跑
        EngineContext context = EngineContext.create()
                .bind("memberId", 1L)
                .bind("eventCode", "DAILY_SIGN")
                .bind("amount", BigDecimal.ZERO)
                .bind("eventTime", LocalDateTime.now())
                .bind("currentMetric", new BigDecimal("7"))
                .bind("payload", Map.of());

        Boolean result = scriptEngine.evaluate(
                ScriptScene.TASK_RULE,
                ExecutableScript.trusted("task/streak", "return currentMetric >= 7;"),
                context,
                Boolean.class);

        assertTrue(result);
    }

    @Test
    @DisplayName("QL 语义备忘：最后一个表达式的值就是返回值，不写 return 也有返回值")
    void qlexpress_returns_the_value_of_the_last_expression() {
        // 这条不是我们的设计，是 QLExpress 4.x 的语言语义，写下来是为了防止
        // 「以为漏写 return 就会返回 null」这个错误判断再次发生
        Boolean result = scriptEngine.evaluate(
                ScriptScene.POOL_ENTRY,
                ExecutableScript.trusted("draw/implicit_return", "memberId > 0;"),
                poolEntryContext(),
                Boolean.class);

        assertTrue(result);
    }

    @Test
    @DisplayName("🔴 分支没覆盖全导致无返回值时明确报错，而不是静默当成「判定不通过」")
    void uncovered_branch_without_return_is_reported_instead_of_silently_false() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ScriptScene.POOL_ENTRY,
                // 只在 if 里 return 了，memberId >= 0 时整段脚本什么都不返回。
                // 这是 QL 下真正会产生 null 的形态，比「漏写 return」隐蔽得多：
                // 平时测试用的账号刚好走 if 分支，上线换个人就静默变成「不准入」
                ExecutableScript.trusted("draw/uncovered_branch", """
                        if (memberId < 0) {
                            return true;
                        }"""),
                poolEntryContext(),
                Boolean.class));

        assertTrue(e.getMessage().contains("没有返回值"),
                "要明确指出脚本没有返回值，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("脚本返回值类型不符场景契约时失败")
    void return_type_violating_scene_contract_is_rejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ScriptScene.POOL_ENTRY,
                ExecutableScript.trusted("draw/wrong_type", "return \"yes\";"),
                poolEntryContext(),
                Boolean.class));

        assertTrue(e.getMessage().contains("返回值类型不匹配"));
    }

    @Test
    @DisplayName("调用方声明的返回类型与场景不一致时立刻失败，挡住场景改动后的调用方漂移")
    void caller_return_type_must_match_scene_contract() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ScriptScene.POOL_ENTRY,
                ExecutableScript.trusted("draw/vip_entry", "return true;"),
                poolEntryContext(),
                String.class));

        assertTrue(e.getMessage().contains("与场景契约不一致"));
    }

    @Test
    @DisplayName("场景契约的变量清单不为空，且必填项都有描述（前端补全依赖它）")
    void every_scene_declares_usable_params() {
        for (ScriptScene scene : ScriptScene.values()) {
            assertFalse(scene.getParams().isEmpty(), scene + " 没有声明任何变量");
            assertNotNull(scene.getReturnType(), scene + " 没有声明返回类型");
            scene.getParams().forEach(param -> assertTrue(
                    param.description() != null && !param.description().isBlank(),
                    scene + "." + param.name() + " 缺描述，前端补全会是空的"));
        }
    }

    // =====================================================================
    // 8. 语法报错
    // =====================================================================

    @Test
    @DisplayName("语法错误在 check 阶段就能捕获，且带得出行号")
    void syntax_error_is_reported_with_line_number() {
        EngineScriptException e = assertThrows(EngineScriptException.class, () -> scriptEngine.check(
                ExecutableScript.untrusted("test/broken", "a = 1;\nreturn (a + ;")));

        assertTrue(e.getScriptErrorDetail().contains("行"),
                "报错详情应带行号，实际为: " + e.getScriptErrorDetail());
    }

    @Test
    @DisplayName("返回值类型不匹配时抛出可读的业务异常")
    void return_type_mismatch_is_rejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptEngine.evaluate(
                ExecutableScript.trusted("test/type", "return \"not a number\";"),
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

        @ScriptFunction(name = "currentTraceId", description = "测试用：从内部数据通道取 traceId")
        public String currentTraceId(EngineContext context) {
            return context.getInternal("traceId", String.class);
        }

        @ScriptFunction(name = "boom", description = "测试用：抛业务异常")
        public String boom() {
            throw new BusinessException("会员不存在");
        }
    }
}
