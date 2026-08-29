package solvela.exception;

import lombok.Data;
import solvela.code.ErrorCode;
import solvela.code.UserErrorCode;

/**
 * 业务逻辑异常。全局异常拦截器按 {@link #errorCode} 翻译成响应体，默认用户级错误码。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2020/8/25 21:57
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class BusinessException extends RuntimeException {

    private String code;

    // ⚠️ 这里原本还有一个 `private String message` 字段，已删除。
    // @Data 会为它生成 getMessage()，而这个方法**覆盖了 Throwable.getMessage()**
    // （Lombok 只看本类声明的方法，看不到继承来的），返回的是那个从未被赋值的字段 —— 永远 null。
    // 后果不是局部的：GlobalExceptionHandler 把 e.getMessage() 直接返给前端，
    // 等于全系统每一个业务异常的提示语都是 null。
    // 删掉字段后 getMessage() 回落到 Throwable 的实现，构造器传进去的 message 才真正生效。
    // 全项目没有任何地方调用过 setMessage()，删除是安全的。

    /**
     * 本次业务失败对应的错误码，决定 HTTP 响应体里的 {@code code} / {@code level}。
     *
     * <p>默认是 {@link UserErrorCode#PARAM_ERROR}（用户级）而不是 SYSTEM_ERROR：业务校验失败
     * 是<b>用户干了不该干的事</b>，不是系统故障。以前 {@code GlobalExceptionHandler} 把所有
     * BusinessException 统一映射成 SYSTEM_ERROR(10001)，后果有两条：
     * 前端拿到的 level 是 system，而监控按「系统错误」计数 —— 于是「运营重复点了一次批量删除」
     * 会和「数据库连不上」出现在同一条告警曲线上，告警从此没人看。
     *
     * <p>需要更精确的码（余额不足、状态已变更等）用 {@link #BusinessException(ErrorCode)} 指定。
     */
    private ErrorCode errorCode = UserErrorCode.PARAM_ERROR;

    public BusinessException() {
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(Throwable cause) {
        super(cause);
    }

    public BusinessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
