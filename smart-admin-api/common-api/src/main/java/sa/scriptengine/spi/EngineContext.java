package sa.scriptengine.spi;

import java.util.Map;

/**
 * 规则执行上下文契约
 */
public interface EngineContext {

    /**
     * 流式绑定参数
     */
    EngineContext bind(String key, Object value);

    /**
     * 批量绑定参数
     */
    EngineContext bindAll(Map<String, Object> variables);

    /**
     * 获取所有变量（供底层 SPI 翻译使用）
     */
    Map<String, Object> getVariables();

    /**
     * 基于已有 Map 快速创建
     */
    public static StandardEngineContext create(Map<String, Object> initialVariables) {
        return new StandardEngineContext(initialVariables);
    }

    /**
     * 获取默认实现的静态工厂
     */
    static EngineContext create() {
        return new StandardEngineContext(null);
    }
}
