package net.lab1024.sa.base.module.support.job.log;

/**
 * 当前线程正在执行哪个 logId —— 供日志 Appender 判断「这条日志属于哪次执行」。
 *
 * <p>⚠️ <b>ThreadLocal 的固有局限必须写在这里，而不是等人踩</b>：
 * 执行器内部若自己起了线程或线程池，那些线程上的日志<b>不会</b>被收集
 * （除非用 {@code ThreadPoolUtil} 提交，它会透传 MDC，但本类是独立的 ThreadLocal）。
 * 表现是「这次执行的日志缺了一大块」，而且完全不报错 ——
 * 不提前说清楚，它会变成一类新的排查负担。
 *
 * <p>为什么不直接复用 MDC：MDC 的值是字符串，而这里要的是「是否开启采集」这个开关语义；
 * 更重要的是 MDC 会被 {@code SmartJobMdcTaskDecorator} 在池线程间搬运，
 * 而日志采集<b>不应该</b>跟着搬 —— 否则子任务的日志会串进父任务的采集缓冲。
 *
 * @author alaric
 * @date 2026-08-11
 */
public final class SmartJobLogContext {

    private static final ThreadLocal<Long> CURRENT_LOG_ID = new ThreadLocal<>();

    private SmartJobLogContext() {
    }

    public static void bind(Long logId) {
        CURRENT_LOG_ID.set(logId);
    }

    public static Long current() {
        return CURRENT_LOG_ID.get();
    }

    /**
     * 🔴 必须在 finally 里调用：池线程是复用的，不清就会把下一个任务的日志
     * 收进上一个任务的缓冲区 —— 那比没有日志更误导人
     */
    public static void clear() {
        CURRENT_LOG_ID.remove();
    }
}
