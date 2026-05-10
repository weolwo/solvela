package net.lab1024.sa.scriptengine.spi;

import org.springframework.util.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 🚀 规则引擎标准上下文实现 (天花板级 API 设计)
 */
public class StandardEngineContext implements EngineContext {

    // 🌟 核心载体：绝不对外直接暴露！
    private final Map<String, Object> variables;

    /**
     * 隐藏构造函数，强制使用静态工厂方法
     */
    StandardEngineContext(Map<String, Object> initialVariables) {
        // 防御性复制：如果外部传了 Map 进来，我们深度 Copy 一份，
        // 绝对不允许外部业务代码通过修改原 Map 来污染 Context！
        this.variables = initialVariables != null ? new HashMap<>(initialVariables) : new HashMap<>();
    }

    /**
     * 优雅的静态工厂入口
     */
    public static StandardEngineContext create() {
        return new StandardEngineContext(null);
    }

    /**
     * 基于已有 Map 快速创建
     */
    public static StandardEngineContext create(Map<String, Object> initialVariables) {
        return new StandardEngineContext(initialVariables);
    }

    // --------------------------------------------------------
    // 🎨 Fluent API 链式绑定区
    // --------------------------------------------------------

    @Override
    public StandardEngineContext bind(String key, Object value) {
        Assert.hasText(key, "Context variable key must not be empty");
        this.variables.put(key, value);
        return this; // 返回自身，实现丝滑链式调用
    }

    @Override
    public StandardEngineContext bindAll(Map<String, Object> map) {
        if (map != null && !map.isEmpty()) {
            this.variables.putAll(map);
        }
        return this;
    }

    @Override
    public Map<String, Object> getVariables() {
        // 终极防御：包裹成不可变视图！
        // 当底层引擎 (QLExpress) 拿到这个 Map 时，它只能读，绝对不能往里瞎 put() 破坏业务上下文！
        return Collections.unmodifiableMap(this.variables);
    }

    /**
     *  类型安全的参数提取（消灭强转的恶心代码）
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(String key, Class<T> targetType) {
        Assert.notNull(targetType, "Target type must not be null");
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
}