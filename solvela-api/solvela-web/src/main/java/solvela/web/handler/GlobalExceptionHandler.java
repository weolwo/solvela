package solvela.web.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.code.SystemErrorCode;
import solvela.code.UserErrorCode;
import solvela.web.ResponseDTO;
import solvela.base.domain.SystemEnvironment;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.exception.BusinessException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常拦截
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2020/8/25 21:57
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @Resource
    private SystemEnvironment systemEnvironment;

    /**
     * json 格式错误 缺少请求体
     */
    @ResponseBody
    @ExceptionHandler({HttpMessageNotReadableException.class})
    public ResponseDTO<?> jsonFormatExceptionHandler(Exception e) {
        if (!systemEnvironment.isProd()) {
            log.error("全局JSON格式错误异常,URL:{}", getCurrentRequestUrl(), e);
        }
        return ResponseDTO.error(UserErrorCode.PARAM_ERROR, "参数JSON格式错误");
    }

    /**
     * json 格式错误 缺少请求体
     */
    @ResponseBody
    @ExceptionHandler({TypeMismatchException.class, BindException.class})
    public ResponseDTO<?> paramExceptionHandler(Exception e) {
        if (e instanceof BindException) {
            if (e instanceof MethodArgumentNotValidException) {
                List<FieldError> fieldErrors = ((MethodArgumentNotValidException) e).getBindingResult().getFieldErrors();
                List<String> msgList = fieldErrors.stream().map(FieldError::getDefaultMessage).collect(Collectors.toList());
                return ResponseDTO.error(UserErrorCode.PARAM_ERROR, String.join(",", msgList));
            }

            List<FieldError> fieldErrors = ((BindException) e).getFieldErrors();
            List<String> error = fieldErrors.stream().map(field -> field.getField() + ":" + field.getRejectedValue()).collect(Collectors.toList());
            String errorMsg = UserErrorCode.PARAM_ERROR.getMsg() + ":" + error;
            return ResponseDTO.error(UserErrorCode.PARAM_ERROR, errorMsg);
        }
        return ResponseDTO.error(UserErrorCode.PARAM_ERROR);
    }

    /**
     * 业务异常。
     *
     * <p>按异常自带的错误码返回（默认 {@link solvela.code.UserErrorCode#PARAM_ERROR}），
     * <b>不再一律映射成 SYSTEM_ERROR(10001)</b>。旧映射有两个后果：
     * 前端拿到的 level 是 system，用户看到的是「系统似乎出现了点小问题」而不是自己填错了什么；
     * 监控按「系统错误」计数，于是「运营重复点了一次批量删除」和「数据库连不上」
     * 落在同一条告警曲线上，告警从此没人看。
     *
     * <p>日志也降为 warn：业务校验失败是预期内的分支，不是需要有人半夜起来看的事故。
     */
    @ResponseBody
    @ExceptionHandler(BusinessException.class)
    public ResponseDTO<?> businessExceptionHandler(BusinessException e) {
        if (!systemEnvironment.isProd()) {
            log.warn("全局业务异常,URL:{}, msg:{}", getCurrentRequestUrl(), e.getMessage());
        }
        return ResponseDTO.error(e.getErrorCode(), e.getMessage());
    }

    /**
     * 其他全部异常
     *
     * @param e 全局异常
     * @return 错误结果
     */
    @ResponseBody
    @ExceptionHandler(Throwable.class)
    public ResponseDTO<?> errorHandler(Throwable e) {
        log.error("捕获全局异常,URL:{}", getCurrentRequestUrl(), e);
        return ResponseDTO.error(SystemErrorCode.SYSTEM_ERROR, systemEnvironment.isProd() ? null : e.toString());
    }

    /**
     * 获取当前请求url
     */
    private String getCurrentRequestUrl() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (null == request) {
            return null;
        }
        ServletRequestAttributes servletRequest = (ServletRequestAttributes) request;
        return servletRequest.getRequest().getRequestURI();
    }

}
