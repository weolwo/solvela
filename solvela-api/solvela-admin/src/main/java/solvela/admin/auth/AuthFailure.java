package solvela.admin.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 认证为什么没通过。由 {@link AdminAuthenticationFilter} 记在 request 上，
 * {@link AdminAuthorizationInterceptor} 读出来决定回哪种 401。
 *
 * <h3>为什么要传这个，而不是让过滤器直接返回错误</h3>
 * 过滤器<b>不知道这个接口需不需要登录</b> —— {@code @AllowAnonymous} 在 handler 上，
 * 而 handler 要等 Spring MVC 完成映射才知道。所以过滤器一律放行、只记下原因，
 * 由拦截器在知道 handler 之后决定这次失败要不要变成一个响应。
 *
 * <p>分两种是因为前端行为不同：{@link #INACTIVE} 弹「长时间未操作，请重新登录」，
 * {@link #INVALID} 直接跳登录页。
 */
public enum AuthFailure {

    /** 带了令牌，但太久没操作（等保的最低活跃频率） */
    INACTIVE,

    /** 没带令牌，或令牌无效 / 已过期 / 已吊销 / 对应的员工已被禁用 */
    INVALID;

    private static final String ATTRIBUTE = AuthFailure.class.getName();

    void markOn(HttpServletRequest request) {
        request.setAttribute(ATTRIBUTE, this);
    }

    /** 读取本次请求的认证失败原因；默认按 {@link #INVALID} 处理 */
    static AuthFailure of(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof AuthFailure failure ? failure : INVALID;
    }
}
