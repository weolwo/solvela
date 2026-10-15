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
import solvela.base.trace.DeviceTrace;
import solvela.member.device.DeviceDispositionService;

import java.io.IOException;

/**
 * 把「这台设备刚刚还在用」记下来。
 *
 * <h3>为什么在这里，而不是在网关</h3>
 * {@code t_device} 在会员域，而网关的 classpath 里<b>没有任何 solvela-base-* 模块、
 * 也没有 mysql 驱动</b>（{@code AppBoundaryTest} 把这条钉死了）。所以刷新活跃时间
 * 只能发生在这一侧。
 *
 * <p>代价是口径变成了「有业务请求」而不是「有任何请求」—— 纯网关内处理的请求
 * （比如令牌校验失败的那些）不会算作活跃。这是对的：那些请求本来也不说明设备在用。
 *
 * <h3>为什么是过滤器，不是在登录里调一次</h3>
 * 只在登录时刷新的话，{@code last_active_time} 实际等于「最后登录时间」，
 * 而那已经有 {@code t_member_login_log} 记着了。这一列要回答的是
 * <b>「这台设备最近还在不在用」</b> —— 一个 30 天没打开过 App 的设备，
 * 和一个天天在用的设备，清理策略和统计口径都不一样。
 *
 * <p>节流在 {@link DeviceDispositionService#touch} 里（默认一小时一次），
 * 所以这里每个请求都调是安全的。
 *
 * <h3>Order 必须排在 DeviceTraceFilter 之后</h3>
 * 设备号是从 MDC 里读的，而往 MDC 里放的是
 * {@code DeviceTraceFilter}（{@code HIGHEST_PRECEDENCE + 1}）。
 * 排到它前面的话 {@link DeviceTrace#id()} 恒为 null，这个过滤器什么都不做，
 * <b>而且不会有任何报错</b>。
 *
 * @Date 2026-09-10
 */
/*
 * 🔴 2026-10-15 从 HIGHEST_PRECEDENCE + 2 挪到 +10：base-web 的 MemberTraceFilter 占了 +2，
 * 两个过滤器同序时相对顺序是不确定的。这里改成 +10，把过滤器链分成两段看得清楚：
 *   +0 ~ +9   绑 MDC（traceId → 设备号 → 会员号）
 *   +10 以后  记活跃（必须排在绑 MDC 之后，否则读到的恒为 null 而且不报错）
 * 本过滤器只读设备号（+1 就绑好了），所以这次挪动不改变任何行为。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class DeviceActivityFilter extends OncePerRequestFilter {

    private final DeviceDispositionService dispositionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        /*
         * 🔴 先放行业务，再记活跃 —— 顺序不能反。
         * 记活跃要访问 Redis、可能要写一次库；放在前面的话，它的延迟会加在
         * 【每一个】请求的响应时间上。而这条记录晚几毫秒没有任何影响。
         *
         * touch 内部吞掉了所有异常，所以这里不需要 try/catch：
         * 一条纯粹的记录不该让用户的请求失败。
         */
        chain.doFilter(request, response);
        dispositionService.touch(DeviceTrace.id());
    }
}
