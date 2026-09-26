package solvela.app.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 从请求里取凭证、往响应里写凭证 cookie —— <b>全网关只有这一处</b>。
 *
 * <h3>两种携带方式，先请求头、后 cookie</h3>
 * <ul>
 *   <li><b>请求头</b>（{@code Authorization} / {@code X-Device-Token}）：原生 App、自动化测试、Postman；</li>
 *   <li><b>HttpOnly cookie</b>：Web 端。页面脚本读不到，XSS 偷不走。</li>
 * </ul>
 * 请求头优先：一个同时带了两者的请求，明确写在头里的那个是调用方的本意。
 *
 * <h3>🔴 为什么必须收在一处</h3>
 * 改用 cookie 之前，{@code MemberLoginController} 自己从 {@code Authorization} 头里取「当前令牌」。
 * 切到 cookie 之后那段代码取到的是 null，而后果全是静默的：
 * 「下线其他设备」把自己也踢掉、「我的登录设备」标不出本机、退出登录吊销不到当前令牌。
 * 所有读凭证的地方都走本类，才不会有哪一处还停留在旧的携带方式上。
 *
 * <h3>cookie 的属性</h3>
 * HttpOnly（脚本读不到）、Secure（只走 HTTPS）、SameSite=Lax（跨站 POST 不带）、
 * Path=/、不带 Domain（只属于当前主机）。secure 时名字带 {@code __Host-} 前缀，
 * 浏览器据此保证兄弟子域写不进同名 cookie。见 {@link WebCookieProperties}。
 *
 * @Date 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class RequestCredentials {

    /** 一份凭证原文，以及它是从哪来的 */
    public record Credential(String value, boolean fromCookie) {
    }

    private final AuthProperties authProperties;

    private final DeviceAuthProperties deviceProperties;

    private final WebCookieProperties cookieProperties;

    // ------------------------------------------------------------------ 读

    /** 会员令牌；没有返回 null。 */
    public Credential session(HttpServletRequest request) {
        String header = bearer(request.getHeader(authProperties.header()));
        if (header != null) {
            return new Credential(header, false);
        }
        String cookie = cookie(request, cookieProperties.sessionCookieName());
        return cookie == null ? null : new Credential(cookie, true);
    }

    /** 会员令牌原文；没有返回 null。 */
    public String sessionValue(HttpServletRequest request) {
        Credential credential = session(request);
        return credential == null ? null : credential.value();
    }

    /** 设备令牌原文；没有返回 null。 */
    public String deviceValue(HttpServletRequest request) {
        String header = request.getHeader(deviceProperties.header());
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return cookie(request, cookieProperties.deviceCookieName());
    }

    // ------------------------------------------------------------------ 写

    /**
     * 写会员令牌 cookie。
     *
     * @param maxAge 有效期；null = 会话 cookie（关浏览器就没了）。「记住我」就靠这个区别
     */
    public void writeSession(HttpServletResponse response, String token, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = base(cookieProperties.sessionCookieName(), token);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    /**
     * 清掉会员令牌 cookie。
     *
     * <p>删除 = 同名、同 Path、同 Domain 再发一次并设 Max-Age=0。属性对不上的话，
     * 浏览器会把它当成另一个 cookie，原来那个删不掉 —— 所以删与写共用 {@link #base}。
     */
    public void clearSession(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                base(cookieProperties.sessionCookieName(), "").maxAge(Duration.ZERO).build().toString());
    }

    /** 写设备令牌 cookie。每次都续满有效期：活跃设备永不过期，长期不来的自然淘汰。 */
    public void writeDevice(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                base(cookieProperties.deviceCookieName(), token).maxAge(cookieProperties.deviceMaxAge())
                        .build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/");
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 去掉 scheme 前缀。前缀比较忽略大小写：各家客户端库对 "Bearer" 的大小写并不统一；
     * 没按约定带前缀的也认 —— 拒绝它只会换来一轮「为什么 401」的排查，而安全性并不依赖这个前缀。
     */
    private String bearer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String scheme = authProperties.scheme();
        if (!scheme.isBlank()) {
            String prefix = scheme + " ";
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                trimmed = trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
