package solvela.biz.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import solvela.base.trace.MemberTrace;
import solvela.member.risk.feature.MemberActivityService;

import java.io.IOException;

/**
 * 把「这个会员刚刚还在用」记下来。{@link DeviceActivityFilter} 的会员版，理由也一样。
 *
 * <h3>为什么在这里，而不是在网关</h3>
 * {@code t_member} 在会员域，而网关的 classpath 里没有 mysql 驱动
 * （{@code AppBoundaryTest} 把这条钉死了）。所以刷新只能发生在这一侧，
 * 会员号则由网关经 {@code X-Member-Id} 传过来。
 *
 * <h3>🔴 为什么不能只在登录时刷新</h3>
 * 会话令牌 30 天有效且不续期 —— 只在登录时刷的话，{@code last_active_time}
 * 实际等于「最后登录时间」，而一个每天都在用的人每 30 天才登录一次。
 * 登录风控会把<b>每个活跃用户每个月判一次「沉睡」</b>，于是人人都要多验一道码。
 *
 * <p>节流在 {@link MemberActivityService#touch} 里（默认一小时一次），所以这里每个请求都调是安全的。
 *
 * <h3>Order 必须排在 {@code MemberTraceFilter} 之后</h3>
 * 会员号是从 MDC 里读的，而往 MDC 里放的是 base-web 的 {@code MemberTraceFilter}
 * （{@code HIGHEST_PRECEDENCE + 2}）。排到它前面的话 {@link MemberTrace#id()} 恒为 null，
 * 这个过滤器什么都不做，<b>而且不会有任何报错</b>。
 *
 * @Date 2026-10-15
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
@RequiredArgsConstructor
public class MemberActivityFilter extends OncePerRequestFilter {

    private final MemberActivityService activityService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 先放行业务，再记活跃 —— 顺序不能反，理由同 DeviceActivityFilter：
        // 记活跃要访问 Redis、可能要写一次库，放前面的话它的延迟会加在每一个请求上
        chain.doFilter(request, response);
        activityService.touch(MemberTrace.id());
    }
}
