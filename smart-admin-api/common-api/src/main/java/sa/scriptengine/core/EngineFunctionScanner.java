package sa.scriptengine.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import sa.scriptengine.annotation.ScriptFunction;
import sa.scriptengine.domain.EngineFunctionMeta;
import sa.scriptengine.spi.EngineContext;
import sa.scriptengine.spi.ScriptDomain;
import sa.scriptengine.spi.ScriptEvaluator;
import sa.scriptengine.spi.ScriptFunctionHandler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
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

    @Override
    public void afterSingletonsInstantiated() {
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
        // 约定：首参声明为 EngineContext 即视为「请求注入执行上下文」，脚本侧不传这个参数
        boolean injectContext = parameters.length > 0
                && EngineContext.class.isAssignableFrom(parameters[0].getType());

        List<String> scriptParams = Arrays.stream(parameters)
                .skip(injectContext ? 1 : 0)
                .map(parameter -> parameter.getType().getSimpleName() + " " + parameter.getName())
                .collect(Collectors.toList());

        return EngineFunctionMeta.builder()
                .domain(domain)
                .functionName(qualifiedName)
                .simpleName(simpleName)
                .targetBean(bean)
                .method(method)
                .injectContext(injectContext)
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
