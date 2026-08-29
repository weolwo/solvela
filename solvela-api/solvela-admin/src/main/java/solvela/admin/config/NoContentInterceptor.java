package solvela.admin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 没有返回值的接口回 <b>204 No Content</b>，而不是 200 + 空 body。
 *
 * <h3>为什么要有这么一个东西</h3>
 * 去掉 {@code ResponseDTO} 之后，「删除成功」这类接口的返回类型自然变成了 {@code void}，
 * 而 Spring MVC 对 void handler 的默认行为是 200 + 零字节。200 加空 body 是个含混的组合：
 * 客户端拿到它无法区分「操作成功，本来就没有内容」和「后端漏写了返回值」。
 * 204 明确表达前者，也是 REST 里对无内容成功响应的既定答案。
 *
 * <h3>为什么不逐个方法加 {@code @ResponseStatus(NO_CONTENT)}</h3>
 * 那要在两百来个方法上各加一行，而且<b>会漏</b> —— 新写的接口没人提醒你加。
 * 漏掉不会报错，只是那一个接口悄悄变回 200，于是同一套 API 里两种约定并存。
 * 收在这里之后规则只有一条，且对以后新增的接口自动生效。
 *
 * <h3>🔴 自己管响应的接口一律不碰</h3>
 * 文件下载、模板导出这些方法也返回 {@code void}，但它们<b>是有 body 的</b> ——
 * 内容直接写进了 {@code HttpServletResponse} 的输出流。给它们改成 204 的后果是
 * 「响应说没有内容，实际带着几百 KB 的 zip」，客户端按状态码行事就会把文件丢掉，
 * 而且小文件还没触发缓冲区提交时改得掉、大文件改不掉 —— 一个按文件大小时灵时不灵的 bug。
 *
 * <p>判据是<b>方法签名里有没有 {@code HttpServletResponse}</b>：拿到了响应对象，
 * 就说明这个接口打算自己决定回什么。再加一道 content-type 的兜底。
 */
@Component
public class NoContentInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        if (method.getReturnType().getParameterType() != void.class) {
            return;
        }
        if (managesOwnResponse(method) || response.getContentType() != null) {
            return;
        }
        if (response.getStatus() != HttpStatus.OK.value() || response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    private static boolean managesOwnResponse(HandlerMethod method) {
        for (MethodParameter parameter : method.getMethodParameters()) {
            if (HttpServletResponse.class.isAssignableFrom(parameter.getParameterType())) {
                return true;
            }
        }
        return false;
    }
}
