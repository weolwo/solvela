package net.lab1024.sa.base.common.exception;

import lombok.Data;
import net.lab1024.sa.base.common.code.ErrorCode;

/**
 * 业务逻辑异常,全局异常拦截后统一返回ResponseCodeConst.SYSTEM_ERROR
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2020/8/25 21:57
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
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

    public BusinessException() {
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String code,String message) {
        super(message);
        this.code=code;
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
