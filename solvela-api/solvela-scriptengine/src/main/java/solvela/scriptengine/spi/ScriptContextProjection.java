package solvela.scriptengine.spi;

/**
 * 把 {@link EngineContext} 投影成某个<b>场景专用的类型化上下文</b>，供脚本函数直接声明为首参。
 *
 * <h3>解决什么</h3>
 * 引擎原本只会注入 {@code EngineContext} 本身。那是个<b>无边界的袋子</b>：
 * 一个函数声明它，你从签名上看不出它到底要什么，函数内部得自己按字符串键取值、自己校验，
 * 于是每个函数抄一遍，抄第二遍时报错文案就已经不一致了。
 *
 * <pre>
 * // 之前：签名说不清依赖，取值与校验散在每个函数里
 * public DrawResultView executeDrawByScript(EngineContext context, String poolCode) {
 *     Long memberId = context.getInternal(ActivityPlayKeys.MEMBER_ID, Long.class);
 *     ...
 * }
 *
 * // 之后：签名就是契约，取值与校验只有一处
 * public DrawResultView executeDrawByScript(ActivityPlayContext play, String poolCode) { ... }
 * </pre>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>实现类声明成 Spring bean，引擎启动扫描时自动收集；</li>
 *   <li>脚本函数把投影类型声明为<b>首参</b>，脚本侧不传这个参数 —— 与直接声明
 *       {@code EngineContext} 是同一套规则；</li>
 *   <li>上下文里缺东西时 {@link #project} 应当<b>抛异常</b>而不是返回残缺对象：
 *       那说明这个函数被用在了没绑上下文的场景里，是编程错误。</li>
 * </ul>
 *
 * <p>🔴 一个类型只能有一个投影实现。两个实现意味着「同一个上下文类型有两种解释」，
 * 引擎在启动时直接拒绝，不去猜该用哪个。
 *
 * @param <T> 场景专用上下文类型
 * @Date 2026-08-31
 */
public interface ScriptContextProjection<T> {

    /**
     * 本投影产出的类型。脚本函数把它声明为首参即可获得注入。
     */
    Class<T> contextType();

    /**
     * 从执行上下文投影出类型化对象。
     *
     * @param context      本次执行的上下文
     * @param functionName 正在调用的脚本函数名（含域前缀）。<b>用于报错</b> ——
     *                     让「这个函数被用在了错误的场景里」这句话能点名是哪个函数，
     *                     调用方因此不需要再手工传一个用途字符串
     */
    T project(EngineContext context, String functionName);
}
