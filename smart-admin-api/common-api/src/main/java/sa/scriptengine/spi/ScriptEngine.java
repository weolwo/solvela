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
     * 按<b>场景契约</b>执行脚本：执行前校验必填变量、执行后校验返回值。业务代码应优先用这个重载。
     *
     * <p>方法名沿用 {@code evaluate} 不是随意的 —— 监控切面的切点是
     * {@code ScriptEngine+.evaluate(..)}，换个名字就不会被计时和异常兜底覆盖。
     *
     * <p>{@code returnType} 与 {@code scene.getReturnType()} 看起来重复，但它是<b>交叉校验</b>：
     * 参数给调用方编译期类型安全，二者不一致时立刻失败 —— 这正好挡住
     * 「几个月后改了场景的返回类型，但调用方还按老类型接」这种漂移。
     *
     * @throws sa.base.common.exception.BusinessException 必填变量缺失、类型不符、脚本无返回值
     */
    <T> T evaluate(ScriptScene scene, ExecutableScript executableScript, EngineContext context, Class<T> returnType);

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
