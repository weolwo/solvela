package solvela.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import solvela.base.web.CurrentUser;

import java.io.IOException;

/**
 * 为每个请求打开身份作用域（{@link CurrentUser}）。
 *
 * <h3>为什么必须是 Filter 而不是 Interceptor</h3>
 * {@code ScopedValue} 的绑定要<b>包住</b>整段执行 —— 语法上就是
 * {@code where(k, v).call(() -> 整个后续流程)}。而 Interceptor 是
 * {@code preHandle} / {@code afterCompletion} 两次独立回调，中间的执行不在任何一个方法体内，
 * 包不住。Filter 的 {@code doFilter} 天然是环绕形态，所以绑定只能在这里。
 *
 * <h3>它不解析身份，只开作用域</h3>
 * 解析身份要看 {@code handler} 上有没有 {@code @AllowAnonymous}，还要按 sa-token 的异常码
 * 区分「登录已过期」和「未登录」（前端据此决定是刷新还是跳登录页）。这两件事 Filter 阶段
 * 都做不到 —— 那时还没进 DispatcherServlet，不知道这个请求会落到哪个方法上。
 *
 * <p>所以分工是：<b>Filter 开作用域，登录拦截器往里写身份</b>。
 * 这样认证逻辑一行都不用动，而作用域的生命周期仍然由结构保证。
 *
 * <h3>顺序</h3>
 * 排在 {@code LogTraceFilter} 之后：traceId 要覆盖到本过滤器可能抛出的异常日志。
 */
@Component
@Order(Integer.MIN_VALUE + 1)
public class RequestScopeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            CurrentUser.openScope(() -> filterChain.doFilter(request, response));
        } catch (IOException | ServletException | RuntimeException | Error e) {
            // doFilter 实际只会抛这几类，原样放行
            throw e;
        } catch (Throwable t) {
            // 编译器要求的兜底：CurrentUser.openScope 的异常类型参数被推断成
            // IOException 与 ServletException 的公共父类 Exception，而本方法只声明了前两者。
            // 与其把 servlet 的异常类型写进 CurrentUser（那会让它依赖 servlet API），
            // 不如在这唯一一处收敛掉。这个分支实际走不到。
            throw new ServletException(t);
        }
    }
}
