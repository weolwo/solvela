package net.lab1024.sa.scriptengine.core;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.exception.EngineScriptException;
import net.lab1024.sa.scriptengine.domain.EngineFunctionMeta;
import net.lab1024.sa.scriptengine.domain.ExecutableScript;
import net.lab1024.sa.scriptengine.spi.EngineContext;
import net.lab1024.sa.scriptengine.spi.ScriptEngine;
import net.lab1024.sa.scriptengine.spi.ScriptEvaluator;
import org.springframework.util.Assert;

/**
 * 默认脚本执行器
 */
@Slf4j
public class DefaultScriptEngine implements ScriptEngine {
    // 依赖倒置：只依赖 SPI 接口，绝不依赖具体的 QLExpress！
    private final ScriptEvaluator evaluator;

    public DefaultScriptEngine(ScriptEvaluator evaluator) {
        Assert.notNull(evaluator, "ScriptEvaluator must not be null");
        this.evaluator = evaluator;
        log.info("🚀 ScriptEngine initialized with [{}] evaluator.", evaluator.name());
    }

    @Override
    public Object evaluate(ExecutableScript script, EngineContext context) {
        Assert.hasText(script.getContent(), "script must not be empty");
        Assert.notNull(context, "RuleContext must not be null");
        Object result = null;
        try {
            boolean checked = evaluator.checkScript(script);
            if (checked) {
                result = evaluator.evaluate(script, context);
            }
            // 委托给底层 SPI 执行
        } catch (Exception e) {
            String message = e.getMessage();
            if (e instanceof EngineScriptException eg) {
                message = eg.getScriptErrorDetail();
            }
            log.error(" script execution failed! Script: \n{},{}", script.getName(), message);
            // 异常收敛：绝不把底层引擎的 ParseException 漏给上层
            throw new BusinessException("脚本执行异常，请检查语法或联系管理员！");
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(ExecutableScript script, EngineContext context, Class<T> returnType) {
        Assert.notNull(returnType, "ReturnType class must not be null");

        Object result = this.evaluate(script, context);
        if (result == null) {
            return null;
        }

        // 严谨的类型边界校验
        if (!returnType.isAssignableFrom(result.getClass())) {
            throw new BusinessException(
                    String.format("规则执行结果类型不匹配! 期望: %s, 实际: %s",
                            returnType.getSimpleName(), result.getClass().getSimpleName())
            );
        }
        return (T) result;
    }

    // 提供给内部的注册入口
    public void registerFunction(EngineFunctionMeta functionMeta) {
        try {
            evaluator.registerFunction(functionMeta);
        } catch (Exception e) {
            log.error("Failed to register function: {}", functionMeta.getFunctionName(), e);
        }
    }
}
