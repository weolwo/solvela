package solvela.scriptengine.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import solvela.scriptengine.annotation.ScriptFunction;
import solvela.scriptengine.domain.EngineFunctionMeta;
import solvela.scriptengine.spi.ScriptContextProjection;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptEvaluator;
import solvela.scriptengine.spi.ScriptFunctionHandler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 启动期扫描所有 {@link ScriptFunctionHandler}，把 {@code @ScriptFunction} 方法绑定到引擎。
 *
 * <p>任何一步不合规都<b>直接让应用启动失败</b>，而不是打条 WARN 继续跑 ——
 * 函数没绑上去的后果是脚本在运行期报「未知函数」，那时候排查成本高得多。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineFunctionScanner implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;

    private final EngineFunctionRegistry registry;

    private final ScriptEvaluator scriptEvaluator;

    /** 类型化上下文投影：contextType -> 投影实现。启动时从容器收集 */
    private final Map<Class<?>, ScriptContextProjection<?>> projections = new HashMap<>();

    /**
     * 收集类型化上下文投影。
     *
     * <p>🔴 一个类型注册了两个投影就直接抛：那意味着「同一个上下文类型有两种解释」，
     * 让引擎去猜用哪个，出问题时没有任何线索能回溯到这一步。
     */
    private void collectProjections() {
        for (String name : applicationContext.getBeanNamesForType(ScriptContextProjection.class)) {
            ScriptContextProjection<?> projection = applicationContext.getBean(name, ScriptContextProjection.class);
            ScriptContextProjection<?> exists = projections.putIfAbsent(projection.contextType(), projection);
            if (exists != null) {
                throw new IllegalStateException(String.format(
                        "脚本上下文投影冲突：类型 [%s] 同时被 %s 与 %s 注册",
                        projection.contextType().getName(),
                        exists.getClass().getName(), projection.getClass().getName()));
            }
        }
        if (!projections.isEmpty()) {
            log.info("[ScriptEngine] 类型化上下文投影 {} 个：{}", projections.size(),
                    projections.keySet().stream().map(Class::getSimpleName).toList());
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        collectProjections();
        String[] beanNames = applicationContext.getBeanNamesForType(ScriptFunctionHandler.class);
        for (String beanName : beanNames) {
            scanHandler(applicationContext.getBean(beanName, ScriptFunctionHandler.class));
        }

        // 全部扫描完再统一绑定：保证「同名冲突」在任何函数生效之前就被发现
        registry.getAllFunctions().forEach(scriptEvaluator::registerFunction);

        logSummary();
    }

    private void scanHandler(ScriptFunctionHandler bean) {
        Class<?> handlerClass = bean.getClass();
        ScriptDomain domain = bean.domain();
        if (domain == null) {
            throw new IllegalStateException("脚本函数处理器 " + handlerClass.getName() + " 的 domain() 返回了 null");
        }

        ReflectionUtils.doWithMethods(handlerClass, method -> {
            ScriptFunction annotation = AnnotationUtils.findAnnotation(method, ScriptFunction.class);
            if (annotation == null) {
                return;
            }
            registry.register(buildMeta(bean, domain, method, annotation));
        });
    }

    private EngineFunctionMeta buildMeta(ScriptFunctionHandler bean, ScriptDomain domain,
                                         Method method, ScriptFunction annotation) {
        String simpleName = annotation.name();
        String qualifiedName = domain.qualify(simpleName);

        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException(String.format(
                    "脚本函数 [%s] 必须是 public 方法：%s#%s",
                    qualifiedName, bean.getClass().getName(), method.getName()));
        }
        if (simpleName.startsWith(domain.getNamespace() + "_")) {
            throw new IllegalStateException(String.format(
                    "脚本函数 [%s] 不要自己写域前缀，引擎会按 domain() 自动加：%s#%s",
                    simpleName, bean.getClass().getName(), method.getName()));
        }

        Parameter[] parameters = method.getParameters();
        /*
         * 约定：首参声明为 EngineContext、或声明为任何已注册投影的类型，
         * 即视为「请求注入执行上下文」，脚本侧不传这个参数。
         *
         * 两种写法的区别只在拿到手的是什么：前者是无边界的袋子，后者是场景专用的类型化对象。
         * 新函数应当优先用后者 —— 签名即契约。
         */
        Class<?> firstType = parameters.length > 0 ? parameters[0].getType() : null;
        ScriptContextProjection<?> projection = firstType == null ? null : projections.get(firstType);
        boolean injectContext = projection != null
                || (firstType != null && EngineContext.class.isAssignableFrom(firstType));

        List<String> scriptParams = Arrays.stream(parameters)
                .skip(injectContext ? 1 : 0)
                .map(parameter -> parameter.getType().getSimpleName() + " " + parameter.getName())
                .collect(Collectors.toList());

        return EngineFunctionMeta.builder()
                .contextProjection(projection)
                .domain(domain)
                .functionName(qualifiedName)
                .simpleName(simpleName)
                .targetBean(bean)
                .method(method)
                .injectContext(injectContext)
                .sideEffect(annotation.sideEffect())
                .description(annotation.description())
                .returnType(method.getReturnType().getSimpleName())
                .params(scriptParams)
                .build();
    }

    private void logSummary() {
        Map<ScriptDomain, Long> countByDomain = registry.countByDomain();
        List<String> lines = new ArrayList<>();
        for (ScriptDomain domain : ScriptDomain.values()) {
            long count = countByDomain.getOrDefault(domain, 0L);
            if (count > 0) {
                lines.add(String.format("  %-10s %-8s %d 个函数", domain.name(), domain.getTitle(), count));
            }
        }
        log.info("[ScriptEngine] 函数绑定完成，共 {} 个：\n{}", registry.size(), String.join("\n", lines));
    }
}
