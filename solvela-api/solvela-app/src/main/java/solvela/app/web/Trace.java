package solvela.app.web;

import org.slf4j.MDC;

/**
 * 本次请求的链路 id。
 *
 * <h3>为什么这里是 MDC 而不是 ScopedValue</h3>
 * 身份（{@code CurrentMember}）用的是 {@link ScopedValue}，那是对的：
 * 值绑在调用栈上，作用域一出就失效，没有可以忘记清理的东西。
 * 链路 id 换不成 ScopedValue，原因不是没想到，是<b>日志框架读不了它</b>：
 *
 * <ul>
 *   <li>logback 的 {@code AsyncAppender} 在<b>入队时</b>调
 *       {@code LoggingEvent.prepareForDeferredProcessing()}，那个方法会调
 *       {@code getMDCPropertyMap()} 把 MDC <b>拷进事件对象</b>，
 *       真正的格式化发生在 worker 线程上。所以 MDC 里的值能跟着事件走，
 *       而一个读 ScopedValue 的自定义 converter 跑在 worker 线程上<b>什么也读不到</b> ——
 *       表现是所有走异步 appender 的日志行 traceId 全空，而这是绝大多数行；</li>
 *   <li>本项目 dev 用 logback、pre/prod/test 用 log4j2，要自己写 converter 得写两份，
 *       两份都有上面那个问题。</li>
 * </ul>
 *
 * <p>所以 MDC 是这里唯一可行的载体，不是偷懒。既然躲不掉 ThreadLocal，
 * 那就把它的<b>生命周期交给语言</b>：{@link #open} 返回一个
 * {@code AutoCloseable}，配 try-with-resources 使用 —— 编译器负责生成 finally，
 * 「忘记清理」这件事不再取决于人有没有记得。
 *
 * <p>这不等同于 ScopedValue 的保证，差别要清楚：ScopedValue 的值<b>不可能</b>
 * 逃出作用域，因为根本没有「设置」这个操作；这里只是让「清理」这一步变得难以遗漏。
 * 一个人执意写 {@code MDC.put} 绕过本类，依然能留下残留 —— 所以还有
 * {@code TraceFilterTest} 那两条断言在守着。
 *
 * <p>⚠️ 与 ScopedValue 一样，<b>不会传播到线程池</b>。丢进 executor 的任务打日志时
 * traceId 是空的。需要的话在提交任务前取出来当参数传，或者给线程池装一个
 * TaskDecorator 显式搬运 —— 但那是要写代码的，不会自己发生。
 */
public final class Trace {

    static final String KEY = "traceId";

    private Trace() {
    }

    /**
     * 绑定链路 id，返回值必须放进 try-with-resources。
     *
     * <pre>{@code
     * try (var ignored = Trace.open(traceId)) {
     *     chain.doFilter(request, response);
     * }
     * }</pre>
     */
    public static MDC.MDCCloseable open(String traceId) {
        return MDC.putCloseable(KEY, traceId);
    }

    /**
     * 当前请求的链路 id；不在请求线程上时返回 null。
     *
     * <p>业务代码<b>一律走这里</b>，不要各自写 {@code MDC.get("traceId")} ——
     * 那个字符串一旦散落在几个文件里，改 key 名就会漏掉一处，而漏掉的表现只是
     * 「这个字段莫名其妙变空了」，不会有任何报错。
     */
    public static String id() {
        return MDC.get(KEY);
    }
}
