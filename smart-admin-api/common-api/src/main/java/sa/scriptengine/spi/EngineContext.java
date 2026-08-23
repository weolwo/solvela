package sa.scriptengine.spi;

import java.util.Map;

/**
 * 脚本执行上下文契约。
 *
 * <p>上下文既是脚本的变量表（脚本里直接按变量名引用），
 * 也是 Java 函数的入参来源 —— 参数表第一位声明为 {@code EngineContext} 的
 * {@code @ScriptFunction} 方法会被引擎自动注入本对象，脚本侧不需要也无法传这个参数。
 */
public interface EngineContext {

    /**
     * 流式绑定单个变量
     */
    EngineContext bind(String key, Object value);

    /**
     * 批量绑定变量
     */
    EngineContext bindAll(Map<String, Object> variables);

    /**
     * 获取所有变量（供底层 SPI 翻译成引擎自己的上下文结构）
     */
    Map<String, Object> getVariables();

    /**
     * 类型安全地取值，类型不符直接抛异常而不是静默返回 null
     */
    <T> T getAs(String key, Class<T> targetType);

    /**
     * 空上下文
     */
    static EngineContext create() {
        return new StandardEngineContext(null);
    }

    /**
     * 基于已有 Map 创建（内部做防御性复制，不会持有外部 Map 的引用）
     */
    static EngineContext create(Map<String, Object> initialVariables) {
        return new StandardEngineContext(initialVariables);
    }
}
