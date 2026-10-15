package solvela.base.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import solvela.base.trace.MemberTrace;
import solvela.trace.MemberContract;

/**
 * 把网关传来的 {@code X-Member-Id} 读回 MDC。{@link DeviceTraceFilter} 的会员版。
 *
 * <p>它是会员号跨进程链路的另一半：网关的 {@code DownstreamClientConfig} 拦截器写这个头，
 * 本过滤器把它读回 MDC。少了任何一半，{@code t_member.last_active_time} 就永远不刷新 ——
 * 而那一列为空的后果是登录风控把所有人都判成「沉睡」，于是人人都要多验一道码。
 *
 * <h3>🔴 本过滤器不做任何鉴别，下游也不许拿它做授权</h3>
 * 见 {@link MemberContract} 的类注释：这个头是被信任的，前提是内院不发布端口。
 * 所以它的用途被限制在「记一笔活跃时间」这种写了也不影响任何人的事情上。
 *
 * <p>Order 排在 {@link DeviceTraceFilter} 之后：三个 MDC 键按 traceId → 设备号 → 会员号
 * 依次绑上，万一本过滤器自己出问题，那条日志里前两个都还在。
 *
 * @Date 2026-10-15
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class MemberTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, java.io.IOException {
        Long memberId = MemberContract.sanitize(request.getHeader(MemberContract.HEADER));
        if (memberId == null) {
            // 匿名接口（登录、注册、发码）本来就没有会员号 —— 这是常态，不绑定就行
            chain.doFilter(request, response);
            return;
        }
        // 🔴 一定要绑（哪怕只是为了覆盖）：容器线程回池之后，下一个【匿名】请求
        // 若不覆盖 MDC，就会挂着上一个请求的会员号 —— 活跃时间会刷到别人头上
        try (MDC.MDCCloseable ignored = MemberTrace.open(memberId)) {
            chain.doFilter(request, response);
        }
    }
}
