package solvela.scriptengine.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import solvela.base.common.exception.BusinessException;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptEvaluator;
import solvela.scriptengine.spi.ScriptScene;

/**
 * 脚本引擎默认门面实现。
 *
 * <p>只依赖 {@link ScriptEvaluator} 这个 SPI，不认识任何具体引擎。
 */
@Slf4j
public class DefaultScriptEngine implements ScriptEngine {

    private final ScriptEvaluator evaluator;

    public DefaultScriptEngine(ScriptEvaluator evaluator) {
        Assert.notNull(evaluator, "ScriptEvaluator must not be null");
        this.evaluator = evaluator;
        log.info("[ScriptEngine] 门面装配完成，底层实现：{}", evaluator.name());
    }

    /**
     * 执行脚本。
     *
     * <p>这里<b>不做语法预校验</b>：校验属于脚本入库/加载环节，放在执行路径上等于每次调用
     * 都多走一遍解析，高频场景下白白吃掉性能，还会把编译缓存越撑越大。
     * 需要校验请显式调用 {@link #check}。
     */
    @Override
    public Object evaluate(ExecutableScript script, EngineContext context) {
        Assert.notNull(script, "ExecutableScript must not be null");
        Assert.notNull(context, "EngineContext must not be null");
        return evaluator.evaluate(script, context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(ExecutableScript script, EngineContext context, Class<T> returnType) {
        Assert.notNull(returnType, "ReturnType class must not be null");

        Object result = this.evaluate(script, context);
        if (result == null) {
            return null;
        }

        if (!returnType.isInstance(result)) {
            throw new BusinessException(String.format(
                    "脚本 [%s] 返回值类型不匹配! 期望: %s, 实际: %s",
                    script.name(), returnType.getSimpleName(), result.getClass().getSimpleName()));
        }
        return (T) result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(ScriptScene scene, ExecutableScript script, EngineContext context, Class<T> returnType) {
        Assert.notNull(scene, "ScriptScene must not be null");
        Assert.notNull(returnType, "ReturnType class must not be null");
        // 交叉校验：挡住「场景改了返回类型、调用方没跟上」的漂移
        if (!returnType.equals(scene.getReturnType())) {
            throw new BusinessException(String.format(
                    "调用方声明的返回类型与场景契约不一致。场景 [%s] 要求: %s, 调用方写的: %s",
                    scene.getTitle(), scene.getReturnType().getSimpleName(), returnType.getSimpleName()));
        }

        // 执行前：必填变量缺失在这里就炸，报的是变量名，不是脚本内部的空指针
        scene.validateInput(context);

        Object result = this.evaluate(script, context);

        // 执行后：null 也算违约 —— 「忘了写 return」伪装成「判定不通过」是最难发现的故障
        scene.validateOutput(script.name(), result);
        return (T) result;
    }

    @Override
    public void check(ExecutableScript script) {
        Assert.notNull(script, "ExecutableScript must not be null");
        evaluator.check(script);
    }

    @Override
    public void clearCompileCache() {
        evaluator.clearCompileCache();
    }
}
