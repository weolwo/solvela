package sa.scriptengine.spi;

import sa.scriptengine.domain.EngineFunctionMeta;
import sa.scriptengine.domain.ExecutableScript;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 脚本求值器 SPI (供底层各个引擎厂商实现)
 */
public interface ScriptEvaluator {

    // 引擎标识
    String name();

    // 检查脚本语法
    boolean checkScript(ExecutableScript executableScript) throws Exception;

    // 编译并执行脚本
    Object evaluate(ExecutableScript executableScript, EngineContext engineContext) throws Exception;

    // 挂载自定义函数
    void registerFunction(EngineFunctionMeta functionMeta) throws Exception;
}
