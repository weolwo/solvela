package solvela.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;

/**
 * 授权：没登录就别想调需要登录的接口。
 *
 * <p>默认全部要登录，{@link Anonymous} 逐个方法开口子。
 *
 * <h3>会员端没有权限模型，也不该假装有</h3>
 * 会员没有角色、没有菜单、没有权限点。C 端真正的授权边界是
 * <b>「只能操作自己的数据」</b>，而这件事拦截器做不到 —— 它不知道
 * {@code /order/123} 里的 123 属于谁。
 *
 * <p>🔴 所以写 C 端接口时：<b>凡是带 id 的操作，都要验这条数据属不属于当前会员</b>。
 * 不要因为「拦截器拦过了」就省掉这一步 —— 它拦的只是「有没有登录」。
 * 这是 C 端最常见的一类越权（水平越权），而且测试很难发现：
 * 用自己的账号点自己的订单，永远是对的。
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            // 静态资源、错误转发等，交给后面的环节
            return true;
        }
        if (method.hasMethodAnnotation(Anonymous.class)) {
            return true;
        }
        if (!CurrentMember.isBound()) {
            // 抛出去交给 ApiExceptionHandler 统一成 401 —— 不在这里手写响应体，
            // 否则错误格式就有了两个来源，改一处忘一处。
            throw new ApiException(ApiErrors.LOGIN_REQUIRED);
        }
        return true;
    }
}
