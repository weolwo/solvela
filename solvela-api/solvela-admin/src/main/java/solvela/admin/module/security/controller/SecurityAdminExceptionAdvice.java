package solvela.admin.module.security.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import solvela.code.UserErrorCode;
import solvela.web.ApiErrorResponse;
import solvela.web.ErrorStatus;

/**
 * 安全中心与登录风控这几组接口的 {@code IllegalArgumentException} → 参数错误（400），消息原样给页面。
 *
 * <h3>为什么只管这两个包</h3>
 * 这里的领域服务刻意用 {@code IllegalArgumentException} 说「你填的不对、为什么」（「灰名单必须给分」
 * 「三个窗口要亚线性」）—— 那句话就是写给运营看的。全局处理器把它当未预期异常回 500，页面上只剩一句
 * 「系统似乎出现了点小问题」，人改不对。
 * 不放到全局：别处的 {@code IllegalArgumentException} 多半来自类库，消息里是内部细节，不该原样给出去。
 *
 * <p>🔴 {@code @Order} 必须比全局处理器靠前：全局那个有 {@code Throwable} 兜底，先轮到它就轮不到这里。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {"solvela.admin.module.security", "solvela.admin.module.member.risk"})
public class SecurityAdminExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> invalid(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[API] {} {} -> PARAM_ERROR {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(ErrorStatus.of(UserErrorCode.PARAM_ERROR))
                .body(new ApiErrorResponse(UserErrorCode.PARAM_ERROR.name(), e.getMessage(), MDC.get("traceId")));
    }
}
