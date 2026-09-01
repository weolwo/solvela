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
import solvela.trace.TraceContract;

import java.io.IOException;

/**
 * 给每个请求一个链路 id，放进 MDC，同时回写响应头。<b>网关自己的一份</b>。
 *
 * <p>日志 pattern 里的 {@code %X{traceId}} 取的就是它；用户截图报障时，
 * 凭响应头里的这一串一次定位到日志。
 *
 * <h3>它是「跨服务链路能不能串起来」的一半</h3>
 * 另一半是 {@code DownstreamClientConfig} 的请求头拦截器：
 * <ul>
 *   <li>本过滤器：把客户端传来的（或自己生成的）id 绑进 MDC；</li>
 *   <li>拦截器：调下游时把 {@code Trace.id()} 写进请求头，biz 侧的过滤器再读回去。</li>
 * </ul>
 * 少了任何一半，两个进程的日志就只能靠时间戳对 —— 而并发下时间戳对不出因果。
 *
 * <p>🔴 sanitize 与生成规则来自 {@link TraceContract}，本类不自己实现：
 * biz 侧有一份自己的 TraceFilter，两份各写各的规则时，网关认的 id 会被那边判非法、
 * 那边于是重新生成一个，链路照样断，而且断得更隐蔽（两边都有 id，只是不一样）。
 *
 * <h3>为什么必须清理，以及危害要说准</h3>
 * <b>下一个请求不会读到旧值</b> —— 它进来第一件事就是覆盖。真正出问题的是<b>请求之间</b>
 * 那段时间：容器线程回到池里之后，任何在这根线程上打的日志都会挂着上一个请求的 traceId，
 * 指向一条毫不相干的调用链 —— 这种日志比没有日志更误导人，因为它看起来完全正常。
 *
 * <p>所以用 {@link Trace#open} 配 try-with-resources，让编译器生成 finally，
 * 而不是靠人记得写。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = TraceContract.sanitize(request.getHeader(Trace.KEY));
        response.setHeader(Trace.KEY, traceId);

        try (MDC.MDCCloseable ignored = Trace.open(traceId)) {
            chain.doFilter(request, response);
        }
    }
}
