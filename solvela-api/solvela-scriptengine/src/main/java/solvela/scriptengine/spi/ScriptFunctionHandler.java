package solvela.scriptengine.spi;

/**
 * 脚本函数处理器契约。
 *
 * <p>所有想把 Java 方法暴露给脚本的类都必须实现本接口并注册成 Spring Bean，
 * 否则 {@link solvela.scriptengine.annotation.ScriptFunction} 注解不会被扫描到。
 *
 * <p>{@link #domain()} 是<b>抽象方法而不是可选注解</b>：这样「新写的 Handler 忘了归类」
 * 在编译期就过不去，而不是等到运行时才发现一堆函数挤在「默认分类」里。
 * 这是本接口存在的主要意义 —— 它不是标记接口。
 *
 * <p>命名约定：实现类叫 {@code <域>XxxHandler}，例如 {@code MemberLevelHandler}、
 * {@code MallStockHandler}。一个域可以有多个 Handler，按业务子领域拆分即可。
 *
 * <p>注意：通过 {@code @ScriptFunction} 暴露的方法会被脚本直接调用，
 * 因此<b>方法内部必须自己做超时控制</b>。引擎的 {@code timeoutMillis} 只能中断脚本自身的
 * 指令循环，无法强行中断已经进入 Java 的 HTTP / DB 调用。
 */
public interface ScriptFunctionHandler {

    /**
     * 本处理器所属业务域，决定了函数名前缀与文档分组。
     */
    ScriptDomain domain();
}
