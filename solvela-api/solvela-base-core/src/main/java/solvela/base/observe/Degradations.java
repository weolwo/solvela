package solvela.base.observe;

import io.micrometer.core.instrument.Metrics;
import solvela.observe.DegradePoint;

/**
 * 设计降级的计数入口：catch 住、不上抛、主流程照走的地方，在落日志的同时调一次 {@link #record}。
 *
 * <p>为什么是静态方法而不是注入 {@code MeterRegistry}：降级点散在 record、工具类、静态解析方法里，
 * 很多地方注入不了 bean。Spring Boot 默认把自动配置的注册表挂到 {@link Metrics#globalRegistry} 上
 * （{@code management.metrics.use-global-registry} 默认 true），所以静态调用也会出现在 /actuator/prometheus 里。
 * 由 {@code DegradationsScrapeTest} 在真实上下文里钉住；有人把那个开关关掉，它会红。
 *
 * <p>网关 classpath 上没有 solvela-base，它的那份在 {@code solvela.app.observe.Degradations}，两份共用
 * {@link DegradePoint#METER} 与 {@link DegradePoint#TAG}。
 */
public final class Degradations {

    private Degradations() {
    }

    public static void record(DegradePoint point) {
        Metrics.counter(DegradePoint.METER, DegradePoint.TAG, point.name()).increment();
    }
}
