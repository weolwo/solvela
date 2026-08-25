package solvela.scriptengine.spi;

import solvela.base.common.exception.EngineScriptException;
import solvela.scriptengine.domain.EngineFunctionMeta;
import solvela.scriptengine.domain.ExecutableScript;

/**
 * 脚本求值器 SPI，由各引擎厂商（QLExpress / Aviator / Groovy）各自实现。
 *
 * <p>本接口是防腐层边界：以上的任何代码都不允许出现 {@code com.alibaba.qlexpress4} 的类型。
 */
public interface ScriptEvaluator {

    /**
     * 引擎标识，用于启动日志与排查
     */
    String name();

    /**
     * 语法预校验，不执行。
     *
     * <p>给「脚本落库前」和「编辑器实时纠错」用。<b>不要</b>在每次执行前调用它 ——
     * 校验会走一次带缓存的解析，高频动态脚本下会把编译缓存撑爆。
     *
     * @throws EngineScriptException 语法有误，异常里带行列号
     */
    void check(ExecutableScript script) throws EngineScriptException;

    /**
     * 执行脚本
     */
    Object evaluate(ExecutableScript script, EngineContext engineContext);

    /**
     * 挂载自定义函数（启动期一次性调用）
     */
    void registerFunction(EngineFunctionMeta functionMeta);

    /**
     * 清空底层编译缓存。
     *
     * <p>QLExpress 4.x 的编译缓存<b>没有容量上限</b>，key 是脚本原文。
     * 一旦有随机拼装的动态脚本进来就是 OOM，必须留出手动清理的口子。
     */
    void clearCompileCache();
}
