package solvela.admin.module.system.job.core;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 把提交线程的 MDC 快照带进执行线程，执行完原样还原。
 *
 * <p>为什么不直接用 {@code solvela-base} 的 {@code ThreadPoolUtil}：那是一个<b>全局静态单例池</b>
 * （固定 5/10、CallerRunsPolicy），不是构建器。定时任务用它就等于和其它业务异步共用队列，
 * 正好破坏「定时任务不能拖垮业务、业务也不能饿死定时任务」这个隔离前提 ——
 * 而资源隔离恰恰是本次重构要立起来的东西。所以这里自建池，只把它那套 MDC 透传逻辑搬过来。
 *
 * <p>🔴 <b>{@code finally} 里必须还原而不是 clear</b>：线程池的线程是复用的，
 * 直接 clear 会把下一个任务的上下文一起抹掉；只 put 不还原则会串号 ——
 * 排查时看到的 traceId 属于上一个任务，比没有 traceId 更误导人。
 *
 * <p>配合 {@code logback-spring.xml} 里已有的 {@code %X{traceId}} 与
 * {@code LogTraceFilter} 的同名 key，定时任务打的业务日志就能和 web 请求走同一套链路检索。
 *
 * @author alaric
 * @date 2026-08-11
 */
public class SolvelaJobMdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            restore(submitterContext);
            try {
                runnable.run();
            } finally {
                restore(backup);
            }
        };
    }

    private static void restore(Map<String, String> contextMap) {
        if (null == contextMap) {
            MDC.clear();
        } else {
            MDC.setContextMap(contextMap);
        }
    }
}
