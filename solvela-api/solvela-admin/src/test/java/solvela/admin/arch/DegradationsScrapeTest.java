package solvela.admin.arch;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import solvela.base.observe.Degradations;
import solvela.observe.DegradePoint;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端进程里，静态的 Degradations.record 真的出现在 Prometheus 抓取结果里。
 *
 * <p>前提是 Spring Boot 把自动配置的注册表挂到 Metrics.globalRegistry 上（management.metrics.use-global-registry，默认 true）。
 * 有人把它关掉，或者换掉了注册表的装配，计数会全部记进一个没人抓的全局注册表 —— 不报错，面板上永远是零。
 */
@SpringBootTest
class DegradationsScrapeTest {

    @Autowired
    private PrometheusMeterRegistry prometheus;

    @Test
    @DisplayName("🔴 记一次降级，抓取结果里能看到对应点位的计数")
    void 记录能被抓到() {
        Degradations.record(DegradePoint.JOB_LANE_REJECTED);

        assertThat(prometheus.scrape())
                .contains("solvela_degraded_total{")
                .contains("point=\"JOB_LANE_REJECTED\"");
    }
}
