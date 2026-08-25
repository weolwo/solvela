package sa.base.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 全链路日志追踪过滤器：给每个请求生成 traceId 并放进 MDC，日志 pattern 里的 %X{traceId} 取的就是它。
 *
 * <p>本类的 package 一直是 {@code sa.base.common.filter}，但文件此前躺在 sa-admin 的源码树里
 * （{@code sa-admin/src/main/java/sa/admin/filter/}）—— javac 不校验目录与 package 是否一致，
 * 所以能编过，代价是它只进了 sa-admin 的 jar。solarx-app 作为第二个启动模块引入时，
 * 依赖 sa-base 却拿不到这个类，只能照抄一份。所以把文件挪到 package 本来该在的位置：
 * 类的全限定名一个字没变，sa-admin 经 sa-base 依赖 + {@code @ComponentScan("sa")} 照常拿到它。
 */
@Slf4j
@Component
@Order(Integer.MIN_VALUE)
public class LogTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            String traceId = request.getHeader(TRACE_ID);
            if (!StringUtils.hasLength(traceId)) {
                // 使用 ThreadLocalRandom 生成正数的随机 Long
                // 并转为字符串存入 MDC
                traceId = Long.toHexString(ThreadLocalRandom.current().nextLong());
            }

            MDC.put(TRACE_ID, traceId);

            // 关键：虽然在 Java 里是数字，但 Header 必须是字符串
            response.setHeader(TRACE_ID, traceId);
            log.info("=================请求开始==================== Method {}  URI {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            log.info("=================请求结束==================== cost {}", System.currentTimeMillis() - start);
            MDC.remove(TRACE_ID);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".ico") || path.endsWith(".png");
    }
}