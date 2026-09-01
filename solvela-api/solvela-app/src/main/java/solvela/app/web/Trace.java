package solvela.app.web;

import org.slf4j.MDC;
import solvela.trace.TraceContract;

/**
 * 本次请求的链路 id。<b>网关自己的一份</b>，不依赖任何 base 模块。
 *
 * <h3>为什么网关不共用 base 的那个</h3>
 * 网关的 pom 里刻意<b>只有 *-api 和 contract</b>，没有任何 base 模块 ——
 * 为了这一个类去依赖 solvela-base-core，代价是连带背上 solvela-model、
 * mybatis-plus-core、spring-security-crypto、bcprov、ip2region(11MB) 共 10 个 jar，
 * 而网关一个都用不上。
 *
 * <p>🔴 但<b>头名与合法性规则不抄</b>，来自 {@link TraceContract}（在 solvela-contract 里，
 * 零第三方依赖、谁都有）。那两样是<b>跨进程协议</b>：网关写头、biz 读头，
 * 两边不一致时链路会静默断掉 —— 两边日志都有 traceId、都很正常，只是对不上。
 * 冗余的是实现，不是约定。
 *
 * <h3>为什么是 MDC 而不是 ScopedValue</h3>
 * 身份（{@code CurrentMember}）用 {@link ScopedValue} 是对的，但链路 id 换不成它：
 * 日志框架读不了。logback 的 {@code AsyncAppender} 在<b>入队时</b>把 MDC 拷进事件对象，
 * 格式化发生在 worker 线程上 —— 一个读 ScopedValue 的 converter 在那里什么也读不到，
 * 表现是所有走异步 appender 的日志行 traceId 全空，而那是绝大多数行。
 *
 * <p>既然躲不掉 ThreadLocal，就把清理交给语言：{@link #open} 返回 {@code AutoCloseable}，
 * 配 try-with-resources，编译器负责生成 finally。
 *
 * <p>⚠️ 不会传播到线程池。丢进 executor 的任务打日志时 traceId 是空的。
 */
public final class Trace {

    /** 见 {@link TraceContract#KEY} —— 这是跨进程约定，不在本类定义 */
    public static final String KEY = TraceContract.KEY;

    private Trace() {
    }

    /** 绑定链路 id，返回值必须放进 try-with-resources */
    public static MDC.MDCCloseable open(String traceId) {
        return MDC.putCloseable(KEY, traceId);
    }

    /**
     * 当前请求的链路 id；不在请求线程上时返回 null。
     *
     * <p>业务代码一律走这里，不要各自写 {@code MDC.get("traceId")} ——
     * 那个字符串散落开之后，改 key 名就会漏掉一处，而漏掉只表现为「这个字段莫名其妙变空了」。
     */
    public static String id() {
        return MDC.get(KEY);
    }
}
