package sa.scriptengine.spi;

import sa.base.common.exception.EngineScriptException;
import sa.scriptengine.domain.ExecutableScript;

/**
 * 脚本引擎门面，业务方唯一应该依赖的入口。
 */
public interface ScriptEngine {

    /**
     * 执行脚本，返回原始结果
     */
    Object evaluate(ExecutableScript executableScript, EngineContext context);

    /**
     * 执行脚本并做返回值类型校验
     */
    <T> T evaluate(ExecutableScript executableScript, EngineContext context, Class<T> returnType);

    /**
     * 语法预校验，供脚本入库前与在线编辑器使用
     *
     * @throws EngineScriptException 语法有误，异常里带行列号
     */
    void check(ExecutableScript executableScript);

    /**
     * 清空底层编译缓存
     */
    void clearCompileCache();
}
