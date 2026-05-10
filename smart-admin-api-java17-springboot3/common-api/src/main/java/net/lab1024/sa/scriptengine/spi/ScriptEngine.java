package net.lab1024.sa.scriptengine.spi;

import net.lab1024.sa.scriptengine.domain.ExecutableScript;

public interface ScriptEngine {

    /**
     * 极简执行，返回 Object
     */
    Object evaluate(ExecutableScript executableScript, EngineContext context);

    /**
     * 泛型推断执行（安全、优雅）
     */
    <T> T evaluate(ExecutableScript executableScript, EngineContext context, Class<T> returnType);
}
