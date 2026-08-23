package sa.scriptengine.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import sa.base.common.exception.BusinessException;
import sa.scriptengine.domain.ExecutableScript;
import sa.scriptengine.spi.EngineContext;
import sa.scriptengine.spi.ScriptEngine;
import sa.scriptengine.spi.ScriptEvaluator;

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
    public void check(ExecutableScript script) {
        Assert.notNull(script, "ExecutableScript must not be null");
        evaluator.check(script);
    }

    @Override
    public void clearCompileCache() {
        evaluator.clearCompileCache();
    }
}
