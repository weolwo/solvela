package solvela.scriptengine.annotation;

import java.lang.annotation.*;

/**
 * 把一个 Java 方法暴露给脚本调用。
 *
 * <p>方法所在的类必须实现 {@link solvela.scriptengine.spi.ScriptFunctionHandler} 并注册为 Spring Bean。
 *
 * <p>方法可以把<b>第一个参数</b>声明为 {@link solvela.scriptengine.spi.EngineContext}，
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
     * {@link solvela.scriptengine.spi.ScriptDomain} 自动拼成 {@code <namespace>_<name>}。
     */
    String name();

    /**
     * 函数用途描述，会直接吐给前端编辑器做悬浮提示
     */
    String description() default "该函数暂无描述";

    /**
     * 本函数<b>有副作用</b>吗（扣库存、发奖、写流水、动账）。
     *
     * <p>标了它，引擎会做一道硬约束：<b>同一次脚本执行里，有副作用的函数最多只准调用一次</b>，
     * 第二次直接抛出。
     *
     * <h3>为什么这道约束必须在引擎里，而不是写在脚本规范文档里</h3>
     * 脚本是运营写的。一段这样的脚本：
     * <pre>
     *   if (次数 &gt; 0) { r = draw_draw('POOL_A'); }
     *   if (是新人)    { r = draw_draw('POOL_NEW'); }   // 两个条件同时成立 → 抽了两次
     * </pre>
     * 语法完全正确、评审时也很难一眼看出，而后果是<b>多发一份奖，且没人会主动来报</b>。
     *
     * <p>另一半理由是一致性：脚本在调用完有副作用的函数之后再抛异常，
     * DB 事务会回滚，但 Redis 的库存预扣、幂等键<b>不会跟着回滚</b>。
     * 限制成一次，并要求它是脚本的最后一步，这个窗口才足够小。
     *
     * <p>⚠️ 纯查询函数不要标 —— 标了会让脚本只能查一次。
     */
    boolean sideEffect() default false;
}
