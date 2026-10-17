package solvela.app.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/**
 * 匿名场景的限流判定：请求体绑定完之后、Controller 方法执行之前（见 {@link RateLimitInterceptor} 类注释）。
 *
 * <p>在这里抛出的 {@code ApiException} 与 Controller 里抛的一样走统一的错误出口，客户端看到的是同一个 429。
 *
 * @Date 2026-10-17
 */
@ControllerAdvice
@RequiredArgsConstructor
public class RateLimitBodyAdvice extends RequestBodyAdviceAdapter {

    private final RateLimiter rateLimiter;

    @Override
    public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return RateLimitAccount.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        checkDeferred(body instanceof RateLimitAccount account ? account.rateLimitAccount() : null);
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                  Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        checkDeferred(null);
        return body;
    }

    private void checkDeferred(String rawAccount) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        Object deferred = request.getAttribute(RateLimitInterceptor.DEFERRED);
        if (deferred instanceof RateLimitScene scene) {
            // 先摘掉标记：判定抛异常时 afterCompletion 不该再补数一次
            request.removeAttribute(RateLimitInterceptor.DEFERRED);
            rateLimiter.check(RateLimitInterceptor.requestOf(request, scene, rawAccount));
        }
    }
}
