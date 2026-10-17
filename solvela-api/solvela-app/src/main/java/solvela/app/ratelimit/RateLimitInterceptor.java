package solvela.app.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import solvela.app.auth.CurrentDevice;
import solvela.app.auth.CurrentMember;
import solvela.app.web.ClientIp;
import solvela.auth.device.DeviceIdentity;

/**
 * 限流的入口：拦截器在 Controller 之前判。
 *
 * <h3>匿名场景要等请求体</h3>
 * 登录、注册、发码、重置密码的「账号」在请求体里。拦截器读请求体会把它读空（后面的 Controller 就拿不到了），
 * 所以这类请求在这里只做标记，真正的判定交给 {@link RateLimitBodyAdvice} —— 它在参数绑定之后、Controller 方法执行之前运行。
 * 请求体压根没被读到（JSON 格式错误，绑定阶段就失败了）时，在 {@link #afterCompletion} 里补数一次，
 * 免得「故意发坏 JSON」成了不被计数的办法。
 *
 * <h3>漏标的接口</h3>
 * 由 {@code RateLimitAnnotationTest} 保证不会出现。万一出现（测试被跳过），按读 / 写落进两个默认场景并 WARN，
 * 而不是不限流。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 请求属性：这次请求的判定推迟到了请求体读完之后 */
    static final String DEFERRED = RateLimitInterceptor.class.getName() + ".deferred";

    private final RateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimitScene scene = sceneOf(method, request);
        if (scene == RateLimitScene.EXEMPT) {
            return true;
        }
        if (scene.anonymous() && !CurrentMember.isBound() && hasAccountBody(method)) {
            request.setAttribute(DEFERRED, scene);
            return true;
        }
        rateLimiter.check(requestOf(request, scene, null));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object deferred = request.getAttribute(DEFERRED);
        if (deferred instanceof RateLimitScene scene) {
            request.removeAttribute(DEFERRED);
            try {
                rateLimiter.check(requestOf(request, scene, null));
            } catch (RuntimeException ignored) {
                // 这次请求已经结束，只是补数；被判罚的话下一次请求会被挡
            }
        }
    }

    static RateLimiter.Request requestOf(HttpServletRequest request, RateLimitScene scene, String rawAccount) {
        DeviceIdentity device = CurrentDevice.find().orElse(null);
        return new RateLimiter.Request(scene, endpointKey(request), ClientIp.of(request),
                device == null ? null : device.deviceId(), device == null ? null : device.issuedAt(),
                CurrentMember.memberIdOrNull(), rawAccount);
    }

    /** {@code METHOD 模板路径}，与网关上报的路由清单同一个写法（{@code RouteReporter}） */
    static String endpointKey(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? null : request.getMethod() + " " + pattern;
    }

    private static RateLimitScene sceneOf(HandlerMethod method, HttpServletRequest request) {
        RateLimit annotation = method.getMethodAnnotation(RateLimit.class);
        if (annotation != null) {
            return annotation.value();
        }
        log.warn("【网关限流】接口没有标 @RateLimit，按默认场景处理: {}", method.getShortLogMessage());
        return "GET".equals(request.getMethod()) ? RateLimitScene.DEFAULT_READ : RateLimitScene.DEFAULT_WRITE;
    }

    private static boolean hasAccountBody(HandlerMethod method) {
        for (MethodParameter p : method.getMethodParameters()) {
            if (p.hasParameterAnnotation(RequestBody.class) && RateLimitAccount.class.isAssignableFrom(p.getParameterType())) {
                return true;
            }
        }
        return false;
    }
}
