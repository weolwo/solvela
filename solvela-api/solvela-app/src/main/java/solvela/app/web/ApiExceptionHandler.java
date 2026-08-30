package solvela.app.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import solvela.base.trace.Trace;

/**
 * 把异常翻成 HTTP 响应。<b>本进程唯一的错误出口。</b>
 *
 * <p>刻意不用 solvela-web 的 {@code GlobalExceptionHandler}：那个是管理端的，
 * 一律返回 200 + {@code ResponseDTO}。两套契约同时存在会让「到底返回什么」
 * 取决于哪个 advice 先匹配上 —— 那是最难查的一类问题。
 * 所以 app 的组件扫描<b>不包含</b> {@code solvela.web}（见 {@code AppApplication}）。
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 业务异常：预期内的失败，不打栈，日志降到 info。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handle(ApiException e, HttpServletRequest request) {
        log.info("[API] {} {} -> {} {}", request.getMethod(), request.getRequestURI(),
                e.error().code(), e.getMessage());
        return build(e.error(), e.getMessage());
    }

    /**
     * 入参校验失败。
     *
     * <p>🔴 把第一条校验信息原样带给用户，而不是笼统的「参数有误」——
     * 「手机号格式不正确」能让人自己改对，「参数有误」只会换来一条工单。
     * 只带第一条：一次告诉用户五个问题，他一个也记不住。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(ApiErrors.INVALID_ARGUMENT.defaultMessage());
        return build(ApiErrors.INVALID_ARGUMENT, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        return build(ApiErrors.INVALID_ARGUMENT, ApiErrors.INVALID_ARGUMENT.defaultMessage());
    }

    /** 请求体不是合法 JSON、字段类型对不上、少了必填参数 —— 都是客户端的问题，400。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception e) {
        return build(ApiErrors.INVALID_ARGUMENT, ApiErrors.INVALID_ARGUMENT.defaultMessage());
    }

    /**
     * 路径没有对应的处理器。
     *
     * <p>🔴 Spring 6.1 起，没匹配到 handler 抛的是 {@code NoResourceFoundException}
     * 而不是 {@code NoHandlerFoundException} —— 两个都要接。
     * 漏了后者的话，访问一个不存在的路径会被兜底分支当成服务端故障，返回 <b>500</b>，
     * 于是监控上会出现一堆根本不存在的「服务端错误」，而真实原因只是前端拼错了 URL。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound() {
        return build(ApiErrors.NOT_FOUND, ApiErrors.NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiErrorResponse("METHOD_NOT_ALLOWED", "请求方式不正确", traceId()));
    }

    /**
     * 兜底。
     *
     * <p>🔴 <b>返回给用户的 message 永远是那句固定文案</b>，异常原文只进日志。
     * 把 {@code e.getMessage()} 透出去，等于把类名、SQL 片段、字段名送给任何人 ——
     * 这是最常见的一种信息泄露，而且看起来像是「贴心的错误提示」。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Throwable e, HttpServletRequest request) {
        log.error("[API] {} {} 未预期异常", request.getMethod(), request.getRequestURI(), e);
        return build(ApiErrors.INTERNAL, ApiErrors.INTERNAL.defaultMessage());
    }

    private ResponseEntity<ApiErrorResponse> build(ApiErrors error, String message) {
        return ResponseEntity.status(error.status())
                .body(new ApiErrorResponse(error.code(), message, traceId()));
    }

    /** 链路 id 由 {@link TraceFilter} 绑定；不在请求线程上时为 null，不自己造一个。 */
    private static String traceId() {
        return Trace.id();
    }
}
