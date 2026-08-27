package solvela.app.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 给每个请求一个链路 id，放进 MDC，同时回写响应头。
 *
 * <p>日志 pattern 里的 {@code %X{traceId}} 取的就是它；
 * 用户截图报障时，凭响应头里的这一串一次定位到日志。
 *
 * <h3>🔴 客户端传来的 id 必须校验</h3>
 * 允许客户端指定 traceId 是有价值的（App 侧能把自己的埋点和服务端日志串起来），
 * 但直接采信就是一个<b>日志注入</b>口子：值里塞一个换行加一段伪造的日志行，
 * 日志文件里就会出现一条看起来完全正常、实际是攻击者写的记录。
 * 采集到 ELK 之后，谁也分不出哪条是真的。
 *
 * <p>所以只接受「32 位以内的字母数字和连字符」。不合规就当没传，自己生成一个 ——
 * 不报错，因为这不是用户该关心的事。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    static final String TRACE_ID = "traceId";

    private static final int MAX_LENGTH = 32;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = sanitize(request.getHeader(TRACE_ID));
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // MDC 是 ThreadLocal 的，容器会复用线程 —— 不清就会把上一个请求的 id 带给下一个
            MDC.remove(TRACE_ID);
        }
    }

    private static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return generate();
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-';
            if (!allowed) {
                return generate();
            }
        }
        return candidate;
    }

    private static String generate() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }
}
