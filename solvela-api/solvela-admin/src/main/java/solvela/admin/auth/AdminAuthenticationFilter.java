package solvela.admin.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import solvela.admin.module.system.login.domain.RequestEmployee;

import java.io.IOException;

/**
 * 认证：把请求头里的令牌换成员工身份，绑进 {@link CurrentEmployee} 的作用域。
 *
 * <p><b>只做认证，不做授权。</b>没带令牌、令牌无效、活跃超时、账号已禁用 ——
 * 一律当作匿名放行，只在 request 上记一笔 {@link AuthFailure}，
 * 由 {@link AdminAuthorizationInterceptor} 决定这个接口允不允许匿名。
 * 分开的理由是两件事的判据不同：认证只看令牌，授权要看被调用的是哪个方法，
 * 而「哪个方法」要等 Spring MVC 完成 handler 映射才知道 —— 过滤器这一层拿不到。
 *
 * <h3>为什么是 Filter 而不是 Interceptor</h3>
 * 身份要绑在 {@link ScopedValue} 的作用域上，而作用域必须<b>包住</b>后续所有代码。
 * 拦截器的 preHandle 是一次方法调用，返回之后作用域就结束了，绑不住。
 * 过滤器可以把 {@code chain.doFilter} 整个包进 {@code ScopedValue.where(...).call(...)} 里。
 *
 * <p>顺带一个好处：作用域随 {@code doFilter} 返回自动失效，
 * 没有「忘了在 finally 里清 ThreadLocal」这种事故 —— 那个类别被结构性地消灭了。
 *
 * <p>排在 {@code LogTraceFilter} 之后：traceId 要覆盖到本过滤器可能打出的日志。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private final TokenStore tokenStore;
    private final EmployeePrincipalLoader principalLoader;
    private final AdminAuthProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = readToken(request);

        switch (tokenStore.lookup(token)) {
            case TokenLookup.Authenticated(AdminSession session) -> {
                RequestEmployee employee = principalLoader.load(session.employeeId(), request);
                if (employee == null) {
                    // 令牌有效但账号已被禁用/删除。令牌本该在禁用时就被吊销，
                    // 走到这里说明那一步漏了 —— 拒绝，并且不告诉调用方是哪种情况
                    AuthFailure.INVALID.markOn(request);
                    chain.doFilter(request, response);
                    return;
                }
                bindAndContinue(employee, new Credential(token, session.superFlag()), request, response, chain);
            }
            case TokenLookup.Inactive ignored -> {
                AuthFailure.INACTIVE.markOn(request);
                chain.doFilter(request, response);
            }
            case TokenLookup.Unknown ignored -> {
                AuthFailure.INVALID.markOn(request);
                chain.doFilter(request, response);
            }
        }
    }

    private void bindAndContinue(RequestEmployee employee, Credential credential, HttpServletRequest request,
                                 HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            ScopedValue.where(CurrentEmployee.EMPLOYEE, employee)
                    .where(CurrentEmployee.CREDENTIAL, credential)
                    .call(() -> {
                        chain.doFilter(request, response);
                        return null;
                    });
        } catch (ServletException | IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            // Carrier.call 的签名把受检异常一并抛出，这里做类型收敛。
            // doFilter 实际只会抛上面那几类，这个分支走不到。
            throw new ServletException(t);
        }
    }

    /**
     * 读请求头里的令牌。没有 / 空串都返回 null，交给 lookup 走 Unknown 分支。
     */
    private String readToken(HttpServletRequest request) {
        String raw = request.getHeader(properties.header());
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String scheme = properties.scheme();
        if (scheme.isBlank()) {
            return raw.trim();
        }
        // 前缀比较忽略大小写：各家客户端库对 "Bearer" 的大小写并不统一
        String prefix = scheme + " ";
        if (raw.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return raw.substring(prefix.length()).trim();
        }
        // 没按约定带前缀的也认 —— 拒绝它只会换来一轮「为什么 401」的排查，
        // 而安全性并不依赖这个前缀
        return raw.trim();
    }
}
