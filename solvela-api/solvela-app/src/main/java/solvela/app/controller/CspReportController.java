package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.Anonymous;
import solvela.app.auth.DeviceExempt;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 接收浏览器上报的 CSP 违规（nginx 的 {@code Content-Security-Policy-Report-Only} 里的 report-uri）。
 *
 * <h3>为什么需要它</h3>
 * CSP 先以 Report-Only 上线：只上报、不拦截。没有这个接口的话，违规只出现在<b>用户自己浏览器的控制台里</b>，
 * 我们一条都看不到 —— 也就永远没有依据把它切成强制执行。策略见 deploy/nginx/snippets/csp-app.conf。
 *
 * <h3>只写日志，不落库</h3>
 * 这是切换强制执行之前那段观察期的临时工具。日志里搜【CSP 违规】，看 violated-directive 与 blocked-uri：
 * 自己的资源被误报 → 调策略；浏览器插件、运营商注入 → 可以忽略；出现陌生的内联脚本 → 那正是它要抓的东西。
 *
 * <h3>🔴 限流</h3>
 * 这是一个匿名、免设备的 POST 接口，而它的唯一动作是写日志 —— 不限的话就是一个免费的日志灌水口。
 * 每分钟最多记 {@link #MAX_PER_MINUTE} 条，超出的只计数，下一分钟开头汇总一次。单条正文截断到 {@link #MAX_BODY}。
 * 进程内计数，多实例各限各的，对这个用途够了。
 *
 * <p>标 {@link Anonymous}、{@link DeviceExempt}：浏览器发报告时不带登录态也不走我们的设备注册。
 * 跨站防护照常生效 —— 报告由我们自己的页面发出，是同源请求。
 *
 * @Date 2026-09-27
 */
@Slf4j
@Tag(name = "CSP 违规上报")
@RestController
public class CspReportController {

    private static final int MAX_PER_MINUTE = 30;

    private static final int MAX_BODY = 2000;

    private final AtomicLong windowStart = new AtomicLong();

    private final AtomicLong loggedInWindow = new AtomicLong();

    private final AtomicLong droppedInWindow = new AtomicLong();

    /**
     * 浏览器发的 Content-Type 是 {@code application/csp-report}（旧版 report-uri）或
     * {@code application/reports+json}（新版 Reporting API）—— 按字符串收，不限定媒体类型。
     */
    @Anonymous
    @DeviceExempt
    @PostMapping("/csp-report")
    public ResponseEntity<Void> report(@RequestBody(required = false) String body) {
        if (admit()) {
            String text = body == null ? "" : body;
            log.warn("【CSP 违规】{}", text.length() > MAX_BODY ? text.substring(0, MAX_BODY) + "…(截断)" : text);
        }
        return ResponseEntity.noContent().build();
    }

    /** 这一条还在本分钟的配额内吗。换窗口时把上一分钟丢弃的数量汇总打一行 */
    private boolean admit() {
        long minute = System.currentTimeMillis() / 60_000;
        long previous = windowStart.get();
        if (previous != minute && windowStart.compareAndSet(previous, minute)) {
            long dropped = droppedInWindow.getAndSet(0);
            loggedInWindow.set(0);
            if (dropped > 0) {
                log.warn("【CSP 违规】上一分钟另有 {} 条超出限流未记录", dropped);
            }
        }
        if (loggedInWindow.incrementAndGet() <= MAX_PER_MINUTE) {
            return true;
        }
        droppedInWindow.incrementAndGet();
        return false;
    }
}
