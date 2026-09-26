package solvela.app.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Web 端凭证 cookie 与跨站写请求防护。
 *
 * <pre>
 * solvela:
 *   app:
 *     cookie:
 *       secure: true                     # dev 在 http://localhost 上跑，配 false
 *       device-max-age: 400d
 *       cross-origin-guard: enforce      # off | enforce
 *       trusted-origins: []              # 额外认作「自己」的源，dev 走 vite 代理时要配
 * </pre>
 *
 * <h3>为什么 Web 端改用 cookie</h3>
 * 令牌放 localStorage 时，页面里混进一段脚本就能把它读走、带回自己的机器长期使用。
 * HttpOnly cookie 页面脚本读不到。原理与取舍见 {@code docs/知识库/Web鉴权-Cookie与浏览器安全边界.md}，
 * 本项目的方案见 {@code docs/业务/会员/账号安全-方案评估与Web端Cookie改造.md} §3。
 *
 * <h3>🔴 secure 与 {@code __Host-} 前缀是绑在一起的</h3>
 * 浏览器只接受「带 Secure、Path=/、不带 Domain」的 {@code __Host-} cookie。所以 secure=false 时
 * 名字自动去掉前缀 —— 否则 dev 上浏览器会静默丢弃这个 cookie，表现是「登录成功但刷新就掉线」，
 * 而服务端日志里一切正常。
 *
 * @param secure           是否带 Secure（并使用 {@code __Host-} 前缀）。只有 HTTPS 下浏览器才会保存 Secure cookie
 * @param deviceMaxAge     设备 cookie 的有效期。Chrome 会把超过 400 天的截断到 400 天
 * @param crossOriginGuard 跨站写请求防护，见 {@link CrossOriginGuard}
 * @param trustedOrigins   除「请求自己的源」之外额外放行的 Origin。只在浏览器不带 Sec-Fetch-Site 时才用得到
 *
 * @Date 2026-09-26
 */
@ConfigurationProperties(prefix = "solvela.app.cookie")
public record WebCookieProperties(Boolean secure, Duration deviceMaxAge, GuardMode crossOriginGuard,
                                  List<String> trustedOrigins) {

    private static final String SESSION = "sv_sess";

    private static final String DEVICE = "sv_dv";

    private static final String HOST_PREFIX = "__Host-";

    public WebCookieProperties {
        // 默认 secure：漏配时最坏是 dev 上登录不上（立刻发现），
        // 反过来默认不 secure 则是生产 cookie 可能走明文（没人发现）
        secure = secure == null || secure;
        deviceMaxAge = deviceMaxAge == null || deviceMaxAge.isNegative() || deviceMaxAge.isZero()
                ? Duration.ofDays(400)
                : deviceMaxAge;
        /*
         * 🔴 默认 ENFORCE，与 step-up / device 的「默认 OFF」相反。
         * 那两者拦的是正常用户可能踩到的东西，默认 OFF 是怕误伤；这里拦的是
         * 「别的网站让浏览器替它发写请求」，正常流量里不存在 —— 而默认 OFF 的后果是
         * cookie 一上线就留着一个 CSRF 口子，且没有任何迹象。
         */
        crossOriginGuard = crossOriginGuard == null ? GuardMode.ENFORCE : crossOriginGuard;
        trustedOrigins = trustedOrigins == null ? List.of() : List.copyOf(trustedOrigins);
    }

    public String sessionCookieName() {
        return secure ? HOST_PREFIX + SESSION : SESSION;
    }

    public String deviceCookieName() {
        return secure ? HOST_PREFIX + DEVICE : DEVICE;
    }

    public enum GuardMode {

        /** 不检查。只用于排障时临时关闭。 */
        OFF,

        /** 浏览器发起的跨站写请求一律 403。 */
        ENFORCE,
    }
}
