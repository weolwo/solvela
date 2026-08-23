package sa.scriptengine.spi;

import java.util.Map;

/**
 * 脚本执行上下文，内部是<b>两条互相隔离的通道</b>。
 *
 * <table border="1">
 *   <caption>两条通道的区别</caption>
 *   <tr><th></th><th>脚本变量通道</th><th>内部数据通道</th></tr>
 *   <tr><td>写</td><td>{@link #bind}</td><td>{@link #bindInternal}</td></tr>
 *   <tr><td>读</td><td>{@link #getAs}</td><td>{@link #getInternal}</td></tr>
 *   <tr><td><b>脚本能读到吗</b></td><td><b>能</b>，按变量名直接引用</td><td><b>不能</b>，完全不可见</td></tr>
 *   <tr><td>Java 函数能读到吗</td><td>能</td><td>能</td></tr>
 * </table>
 *
 * <p><b>为什么要拆：</b>脚本是运营在写的，脚本变量是给他们用的输入参数。
 * 但 traceId、操作人、幂等键、活动快照这类东西 Java 函数需要、<b>却不该出现在运营的视野里</b> ——
 * 它们既不是运营该关心的，暴露出去也等于把内部实现细节变成了事实契约，以后想改都改不动。
 * 拆开之前这两者混在一个 Map 里，只能全给或全不给。
 *
 * <p>Java 函数拿到本对象的方式：把 {@code @ScriptFunction} 方法的<b>首参</b>声明为
 * {@code EngineContext}，引擎会自动注入，脚本侧不感知这个参数。
 */
public interface EngineContext {

    // ------------------------------------------------------------------
    // 脚本变量通道：脚本可见
    // ------------------------------------------------------------------

    /**
     * 绑定一个<b>脚本可见</b>的变量，脚本里按 key 直接当变量名引用
     */
    EngineContext bind(String key, Object value);

    /**
     * 批量绑定脚本可见变量
     */
    EngineContext bindAll(Map<String, Object> variables);

    /**
     * 类型安全地读取脚本变量，类型不符直接抛异常而不是静默返回 null
     */
    <T> T getAs(String key, Class<T> targetType);

    /**
     * 取出要喂给脚本的那份变量表，<b>仅供底层 SPI 翻译成引擎自己的上下文结构</b>。
     *
     * <p>🔴 这是脚本可见性的<b>唯一出口</b>：只有从这里流出去的东西脚本才读得到。
     * 内部数据通道的内容绝不出现在返回值里。
     */
    Map<String, Object> getScriptVariables();

    // ------------------------------------------------------------------
    // 内部数据通道：脚本不可见，只有 Java 函数能读
    // ------------------------------------------------------------------

    /**
     * 绑定一条<b>脚本不可见</b>的内部数据（traceId、操作人、幂等键、活动快照等）
     */
    EngineContext bindInternal(String key, Object value);

    /**
     * 类型安全地读取内部数据，类型不符直接抛异常
     */
    <T> T getInternal(String key, Class<T> targetType);

    // ------------------------------------------------------------------
    // 工厂
    // ------------------------------------------------------------------

    /**
     * 空上下文
     */
    static EngineContext create() {
        return new StandardEngineContext(null);
    }

    /**
     * 基于已有 Map 创建<b>脚本变量</b>（内部做防御性复制，不持有外部 Map 引用）
     */
    static EngineContext create(Map<String, Object> initialVariables) {
        return new StandardEngineContext(initialVariables);
    }
}
