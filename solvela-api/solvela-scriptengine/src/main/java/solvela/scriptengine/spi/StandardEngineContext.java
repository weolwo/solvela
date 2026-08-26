package solvela.scriptengine.spi;

import org.springframework.util.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link EngineContext} 的默认实现。
 *
 * <p>两条通道是两个物理隔离的 Map，不是一个 Map 加前缀约定 ——
 * 后者只要有人拼错前缀就漏了，前者漏不了。
 */
public class StandardEngineContext implements EngineContext {

    /**
     * 脚本可见
     */
    private final Map<String, Object> variables;

    /**
     * 脚本不可见，只有 Java 函数能读。懒初始化，绝大多数执行用不到
     */
    private Map<String, Object> internals;

    StandardEngineContext(Map<String, Object> initialVariables) {
        // 防御性复制：不持有外部 Map 的引用，避免业务方改原 Map 污染执行中的上下文
        this.variables = initialVariables != null ? new HashMap<>(initialVariables) : new HashMap<>();
    }

    public static StandardEngineContext create() {
        return new StandardEngineContext(null);
    }

    public static StandardEngineContext create(Map<String, Object> initialVariables) {
        return new StandardEngineContext(initialVariables);
    }

    // ------------------------------------------------------------------
    // 脚本变量通道
    // ------------------------------------------------------------------

    @Override
    public StandardEngineContext bind(String key, Object value) {
        Assert.hasText(key, "脚本变量名不能为空");
        this.variables.put(key, value);
        return this;
    }

    @Override
    public StandardEngineContext bindAll(Map<String, Object> map) {
        if (map != null && !map.isEmpty()) {
            this.variables.putAll(map);
        }
        return this;
    }

    @Override
    public <T> T getAs(String key, Class<T> targetType) {
        return cast(this.variables.get(key), key, targetType, "脚本变量");
    }

    /**
     * 脚本可见性的唯一出口。返回不可变视图，且<b>只含脚本变量</b>，内部数据不在其中。
     */
    @Override
    public Map<String, Object> getScriptVariables() {
        return Collections.unmodifiableMap(this.variables);
    }

    // ------------------------------------------------------------------
    // 内部数据通道
    // ------------------------------------------------------------------

    @Override
    public StandardEngineContext bindInternal(String key, Object value) {
        Assert.hasText(key, "内部数据名不能为空");
        if (this.internals == null) {
            this.internals = new HashMap<>();
        }
        this.internals.put(key, value);
        return this;
    }

    @Override
    public <T> T getInternal(String key, Class<T> targetType) {
        Object value = this.internals == null ? null : this.internals.get(key);
        return cast(value, key, targetType, "内部数据");
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> T cast(Object value, String key, Class<T> targetType, String channel) {
        Assert.notNull(targetType, "目标类型不能为空");
        if (value == null) {
            return null;
        }
        if (!targetType.isInstance(value)) {
            throw new IllegalArgumentException(
                    String.format("%s [%s] 类型不匹配! 期望: %s, 实际: %s",
                            channel, key, targetType.getSimpleName(), value.getClass().getSimpleName())
            );
        }
        return (T) value;
    }

    /**
     * 只打 key 不打 value：内部数据可能含敏感信息，日志里不能出现
     */
    @Override
    public String toString() {
        return "EngineContext{变量=" + variables.keySet()
                + ", 内部数据=" + (internals == null ? "[]" : internals.keySet()) + "}";
    }
}
