package solvela.web.handler;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import solvela.base.domain.SystemEnvironment;
import solvela.code.ErrorCode;
import solvela.code.SystemErrorCode;
import solvela.code.UserErrorCode;
import solvela.exception.BusinessException;
import solvela.web.ApiErrorResponse;
import solvela.web.ErrorStatus;

/**
 * 把异常翻成 HTTP 响应。<b>管理端唯一的错误出口。</b>
 *
 * <p>上一版这里返回的是 200 + {@code ResponseDTO}，而且不是唯一出口 ——
 * {@code AdminInterceptor} 用 {@code SolvelaResponseUtil.write} 自己手写了三种错误响应。
 * 于是「错误长什么样」有两个来源，改一处忘一处；那三种（未登录、活跃超时、无权限）
 * 恰恰是前端最常遇到的。现在拦截器抛异常，格式只在这里定义一次。
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2020/8/25 21:57
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Resource
    private SystemEnvironment systemEnvironment;

    /**
     * 业务异常：预期内的失败。
     *
     * <p>状态码由 {@link ErrorStatus} 按错误码决定，默认 {@code PARAM_ERROR} → 400。
     * 上一版一律映射成 {@code SYSTEM_ERROR}，后果有两条：用户看到的是「系统似乎出现了点小问题」
     * 而不是自己填错了什么；监控按「系统错误」计数，于是「运营重复点了一次批量删除」
     * 和「数据库连不上」落在同一条告警曲线上，告警从此没人看。
     *
     * <p>日志是 warn 不是 error：业务校验失败是预期内的分支，不是需要有人半夜起来看的事故。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> businessException(BusinessException e, HttpServletRequest request) {
        log.warn("[API] {} {} -> {} {}", request.getMethod(), request.getRequestURI(),
                e.getErrorCode().name(), e.getMessage());
        return build(e.getErrorCode(), e.getMessage());
    }

    /**
     * 入参校验失败。
     *
     * <p>🔴 把第一条校验信息原样带给用户，而不是笼统的「参数错误」——
     * 「手机号格式不正确」能让人自己改对，「参数错误」只会换来一条工单。
     * 只带第一条：一次告诉用户五个问题，他一个也记不住。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> validationException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(UserErrorCode.PARAM_ERROR.getMsg());
        return build(UserErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> methodValidationException() {
        return build(UserErrorCode.PARAM_ERROR, UserErrorCode.PARAM_ERROR.getMsg());
    }

    /** 请求体不是合法 JSON、字段类型对不上、少了必填参数 —— 都是客户端的问题，400 */
    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> malformedRequest(Exception e, HttpServletRequest request) {
        if (!systemEnvironment.isProd()) {
            log.warn("[API] {} {} 请求格式错误", request.getMethod(), request.getRequestURI(), e);
        }
        return build(UserErrorCode.PARAM_ERROR, "请求参数格式错误");
    }

    /**
     * 路径没有对应的处理器。
     *
     * <p>🔴 Spring 6.1 起，没匹配到 handler 抛的是 {@code NoResourceFoundException}
     * 而不是 {@code NoHandlerFoundException} —— 两个都要接。漏了的话，
     * 访问一个不存在的路径会被兜底分支当成服务端故障返回 <b>500</b>，
     * 于是监控上多出一堆根本不存在的「服务端错误」，真实原因只是前端拼错了 URL。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> notFound() {
        return build(UserErrorCode.DATA_NOT_EXIST, "请求的接口不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> methodNotAllowed() {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方式不正确");
    }

    /** 上传超限：413 而不是 500，前端据此提示「文件太大」而不是「服务开小差」 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> uploadTooLarge() {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "文件超过大小限制");
    }

    /**
     * 兜底。
     *
     * <p>🔴 <b>返回给用户的 message 永远是那句固定文案</b>，异常原文只进日志。
     * 把 {@code e.getMessage()} 透出去，等于把类名、SQL 片段、字段名送给任何人 ——
     * 这是最常见的一种信息泄露，而且看起来像是「贴心的错误提示」。
     * 非生产环境例外：本地调试时看不到原因才是真的浪费时间。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Throwable e, HttpServletRequest request) {
        log.error("[API] {} {} 未预期异常", request.getMethod(), request.getRequestURI(), e);
        String message = systemEnvironment.isProd() ? SystemErrorCode.SYSTEM_ERROR.getMsg() : e.toString();
        return build(SystemErrorCode.SYSTEM_ERROR, message);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode errorCode, String message) {
        return respond(ErrorStatus.of(errorCode), errorCode.name(),
                message == null || message.isBlank() ? errorCode.getMsg() : message);
    }

    /** traceId 由 {@code LogTraceFilter} 放进 MDC；同一个值也在 {@code traceId} 响应头上 */
    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, MDC.get("traceId")));
    }
}
