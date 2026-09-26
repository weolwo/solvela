package solvela.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;

import java.util.Locale;
import java.util.Set;

/**
 * 跨站写请求防护（CSRF）：<b>浏览器发起的写请求只认同源</b>。
 *
 * <h3>为什么需要它</h3>
 * Web 端改用 cookie 之后，浏览器会<b>自动</b>带上会话 cookie —— 包括别的网站诱导它发出的请求。
 * SameSite=Lax 挡住了跨站 POST，但挡不住<b>同站</b>：{@code admin.} 与 {@code app.} 属于同一个 site。
 *
 * <h3>判据：浏览器自己填、页面脚本改不了的两个头</h3>
 * <pre>
 *   非写请求（GET / HEAD / OPTIONS / TRACE）        → 放行
 *   Sec-Fetch-Site = same-origin / none              → 放行（none = 用户自己在地址栏 / 书签发起）
 *   Sec-Fetch-Site = 其它（same-site / cross-site）   → 拒绝
 *   没有 Sec-Fetch-Site，有 Origin                    → Origin 是自己的源才放行（老浏览器）
 *   两个都没有                                        → 不是浏览器（App / Postman / curl），放行
 * </pre>
 * 与 Go 1.25 标准库 {@code http.CrossOriginProtection} 同一套逻辑。原理见知识库《Web鉴权》§4.3。
 *
 * <h3>🔴 对所有写请求生效，不只是「已登录的」</h3>
 * 登录、注册这类匿名写接口也要挂：否则 evil.com 能用<b>攻击者自己的</b>账号替受害者提交一次登录，
 * 受害者浏览器里落下攻击者的会话 —— 此后他绑的地址、充的钱全进了攻击者的号（登录 CSRF）。
 *
 * <h3>为什么不用 CSRF 令牌、也不靠「必须带自定义头」</h3>
 * 令牌方案多一个 cookie、前端要读 cookie 搬进请求头，还要靠 {@code __Host-} 防兄弟子域覆盖；
 * 自定义头方案依赖 CORS 永远配对 —— 而管理端的 {@code CorsFilterConfig} 在 dev / test 就是
 * 「允许任意来源 + 携带凭证」。这两个头由浏览器填，不依赖任何配置。
 *
 * <p>排在所有拦截器最前：来源不对的请求，连「你是谁」都不必问。
 *
 * @Date 2026-09-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossOriginGuardInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

    private static final String ORIGIN = "Origin";

    private final WebCookieProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (properties.crossOriginGuard() == WebCookieProperties.GuardMode.OFF) {
            return true;
        }
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        if (allowed(request)) {
            return true;
        }
        log.warn("【跨站防护】拒绝浏览器发起的跨源写请求, {} {}, Sec-Fetch-Site: {}, Origin: {}",
                request.getMethod(), request.getRequestURI(),
                request.getHeader(SEC_FETCH_SITE), request.getHeader(ORIGIN));
        throw new ApiException(ApiErrors.FORBIDDEN, "请求来源不被允许");
    }

    private boolean allowed(HttpServletRequest request) {
        String site = request.getHeader(SEC_FETCH_SITE);
        if (site != null && !site.isBlank()) {
            String s = site.trim().toLowerCase(Locale.ROOT);
            return "same-origin".equals(s) || "none".equals(s);
        }
        String origin = request.getHeader(ORIGIN);
        if (origin == null || origin.isBlank()) {
            // 两个头都没有：不是浏览器。它带的是调用者自己的凭证，不存在「借用」
            return true;
        }
        String o = stripTrailingSlash(origin.trim());
        return o.equalsIgnoreCase(selfOrigin(request)) || properties.trustedOrigins().stream()
                .anyMatch(trusted -> stripTrailingSlash(trusted.trim()).equalsIgnoreCase(o));
    }

    /**
     * 这个请求自己的源。在 nginx 后面时，协议取 {@code X-Forwarded-Proto}，主机取 {@code Host}
     * （nginx 原样透传了 {@code $host}，见 deploy/nginx/snippets/proxy-upstream.conf）。
     *
     * <p>dev 走 vite 代理时 Host 被改写（changeOrigin），这里算出来的是 127.0.0.1:1025 而不是页面的源 ——
     * 那种情况靠配置里的 {@code trusted-origins} 兜住。而且只有不带 Sec-Fetch-Site 的老浏览器才会走到这一步。
     */
    private static String selfOrigin(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String scheme = proto == null || proto.isBlank() ? request.getScheme() : proto.trim();
        String host = request.getHeader("Host");
        return scheme + "://" + (host == null ? request.getServerName() : host.trim());
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
