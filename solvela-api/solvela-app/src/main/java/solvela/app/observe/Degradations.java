package solvela.app.observe;

import io.micrometer.core.instrument.Metrics;
import solvela.observe.DegradePoint;

/**
 * 网关的设计降级计数入口。与 {@code solvela.base.observe.Degradations} 是同一个实现 ——
 * 网关 classpath 上刻意没有任何 solvela-base 模块（{@code AppBoundaryTest}），所以在这里各写一份，
 * 指标名与标签名都取自 {@link DegradePoint}，两边不会漂。
 */
public final class Degradations {

    private Degradations() {
    }

    public static void record(DegradePoint point) {
        Metrics.counter(DegradePoint.METER, DegradePoint.TAG, point.name()).increment();
    }
}
