package sa.scriptengine.annotation;

import java.lang.annotation.*;

/**
 * 把一个 Java 方法暴露给脚本调用。
 *
 * <p>方法所在的类必须实现 {@link sa.scriptengine.spi.ScriptFunctionHandler} 并注册为 Spring Bean。
 *
 * <p>方法可以把<b>第一个参数</b>声明为 {@link sa.scriptengine.spi.EngineContext}，
 * 引擎会自动注入当前执行上下文，脚本侧不感知这个参数。例如：
 * <pre>
 *   &#64;ScriptFunction(name = "getLevel", description = "取当前会员等级")
 *   public Integer getLevel(EngineContext ctx) { ... }
 *   // 脚本里写：member_getLevel()
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScriptFunction {

    /**
     * 域内短名，不要自己加域前缀 —— 引擎会按 Handler 声明的
     * {@link sa.scriptengine.spi.ScriptDomain} 自动拼成 {@code <namespace>_<name>}。
     */
    String name();

    /**
     * 函数用途描述，会直接吐给前端编辑器做悬浮提示
     */
    String description() default "该函数暂无描述";
}
