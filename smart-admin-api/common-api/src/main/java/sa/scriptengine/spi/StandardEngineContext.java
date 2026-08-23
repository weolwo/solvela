package sa.scriptengine.spi;

import org.springframework.util.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link EngineContext} 的默认实现
 */
public class StandardEngineContext implements EngineContext {

    private final Map<String, Object> variables;

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

    @Override
    public StandardEngineContext bind(String key, Object value) {
        Assert.hasText(key, "上下文变量名不能为空");
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

    /**
     * 返回不可变视图。底层引擎拿到的是<b>它的副本</b>（见 QLExpressEvaluator），
     * 这里返回不可变只是为了防止调用方拿去乱改。
     */
    @Override
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(this.variables);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAs(String key, Class<T> targetType) {
        Assert.notNull(targetType, "目标类型不能为空");
        Object value = this.variables.get(key);

        if (value == null) {
            return null;
        }

        if (!targetType.isInstance(value)) {
            throw new IllegalArgumentException(
                    String.format("上下文变量 [%s] 类型不匹配! 期望: %s, 实际: %s",
                            key, targetType.getSimpleName(), value.getClass().getSimpleName())
            );
        }
        return (T) value;
    }

    @Override
    public String toString() {
        return "EngineContext" + variables.keySet();
    }
}
