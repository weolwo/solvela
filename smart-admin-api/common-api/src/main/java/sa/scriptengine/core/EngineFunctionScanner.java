package sa.scriptengine.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sa.base.common.util.JsonUtils;
import sa.base.module.support.scriptengine.annotation.ScriptFunctionGroup;
import sa.scriptengine.annotation.ScriptFunction;
import sa.scriptengine.domain.EngineFunctionMeta;
import sa.scriptengine.spi.ScriptEngineFunctionHandler;
import sa.scriptengine.spi.ScriptEvaluator;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 🚀 纯粹的 Spring Bean 扫描器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineFunctionScanner implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final EngineFunctionRegistry registry; // 注入中央注册表
    private final ScriptEvaluator scriptEvaluator;
    @Override
    public void afterSingletonsInstantiated() {
        String[] beanNames = applicationContext.getBeanNamesForType(ScriptEngineFunctionHandler.class);
        for (String beanName : beanNames) {
            ScriptEngineFunctionHandler bean = (ScriptEngineFunctionHandler) applicationContext.getBean(beanName);
            ScriptFunctionGroup groupAnno = AnnotationUtils.findAnnotation(bean.getClass(), ScriptFunctionGroup.class);
            // 如果没写注解，给个兜底的分类名
            String handlerName = (groupAnno != null) ? groupAnno.value() : "默认通用工具";
            ReflectionUtils.doWithMethods(bean.getClass(), method -> {
                ScriptFunction annotation = AnnotationUtils.findAnnotation(method, ScriptFunction.class);
                if (annotation == null) {
                    return;
                }
                // 组装大一统元数据
                EngineFunctionMeta meta = EngineFunctionMeta.builder()
                        .handlerName(handlerName)
                        .functionName(annotation.name())
                        .description(annotation.description())
                        .targetBean(bean)
                        .method(method)
                        .returnType(method.getReturnType().getSimpleName())
                        .params(Arrays.stream(method.getParameters())
                                .map(p -> p.getType().getSimpleName() + " " + p.getName())
                                .collect(Collectors.toList()))
                        .build();

                // 塞进注册表，打完收工！
                registry.register(meta);
                log.debug("✅ 规则函数扫描完毕: {}", annotation.name());
            });
        }
        registry.getAllFunctions().forEach(e-> {
            try {
                scriptEvaluator.registerFunction(e);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        if (log.isInfoEnabled()) {
            log.info("🚀 [Script Engine] 所有规则函数加载完毕:\n{}", JsonUtils.toJson(registry.exportDocs()));
        }
        log.info("🚀 [Script Evaluator Engine] 扫描完成，共收集 {} 个自定义规则函数!", registry.getAllFunctions().size());
    }
}