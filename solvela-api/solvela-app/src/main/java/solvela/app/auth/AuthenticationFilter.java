package solvela.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 认证：把请求头里的令牌换成会员身份，绑进 {@link CurrentMember} 的作用域。
 *
 * <p><b>只做认证，不做授权。</b>没带令牌、令牌无效、会员已冻结 —— 一律当作匿名放行，
 * 由 {@link AuthorizationInterceptor} 去判断这个接口允不允许匿名。
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
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private final TokenStore tokenStore;
    private final MemberPrincipalLoader principalLoader;
    private final AuthProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        MemberPrincipal principal = authenticate(request);

        if (principal == null) {
            // 匿名。ScopedValue 不接受 null 值，所以不绑定 ——
            // 于是 CurrentMember.isBound() 就是「有没有登录」的准确答案，不需要再判空。
            chain.doFilter(request, response);
            return;
        }

        try {
            ScopedValue.where(CurrentMember.MEMBER, principal).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Carrier.call 的签名把受检异常一并抛出，这里做类型收敛。
            // doFilter 只会抛上面那三类，走到这里说明 JDK 的签名比实际更宽。
            throw new ServletException(e);
        }
    }

    private MemberPrincipal authenticate(HttpServletRequest request) {
        String token = readToken(request);
        if (token == null) {
            return null;
        }
        Long memberId = tokenStore.resolve(token);
        if (memberId == null) {
            return null;
        }
        // 令牌有效但会员被冻结/注销时返回 null —— 状态判断收在 loader 里，
        // 「什么算一个可用身份」只有一个地方定义。
        return principalLoader.load(memberId);
    }

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
        // 没按约定带前缀的，也认 —— 拒绝它只会换来一轮「为什么 401」的排查，
        // 而安全性并不依赖这个前缀。
        return raw.trim();
    }
}
