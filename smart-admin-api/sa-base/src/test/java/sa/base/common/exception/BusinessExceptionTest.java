package sa.base.common.exception;

import sa.base.common.code.UserErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * getMessage() 必须真的返回构造时传进去的那句话。
 *
 * <p>这条测试看着像废话，但它挡的是一个真实发生过的事故：
 * 类上有 {@code @Data}，同时又声明了一个 {@code private String message} 字段，
 * Lombok 便为它生成了 {@code getMessage()} —— 这个方法<b>覆盖掉 Throwable.getMessage()</b>
 * （Lombok 只看本类声明的方法，看不到继承来的），返回那个从未被赋值的字段，结果恒为 null。
 *
 * <p>而 {@code GlobalExceptionHandler} 是直接把 {@code e.getMessage()} 返给前端的 ——
 * 等于全系统每一个业务异常的提示语都是 null，而且不报错、不抛异常，纯静默。
 *
 * @Date 2026-08-08
 */
public class BusinessExceptionTest {

    @Test
    public void 单参构造的消息要能取回来() {
        assertEquals("库存不足", new BusinessException("库存不足").getMessage());
    }

    @Test
    public void 带错误码的构造消息同样要能取回来() {
        BusinessException e = new BusinessException("A0001", "库存不足");
        assertEquals("库存不足", e.getMessage());
        assertEquals("A0001", e.getCode());
    }

    @Test
    public void 带cause的构造消息同样要能取回来() {
        IllegalStateException cause = new IllegalStateException("底层原因");
        BusinessException e = new BusinessException("库存不足", cause);
        assertEquals("库存不足", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test
    public void ErrorCode构造走的是错误码自带的文案() {
        assertEquals(UserErrorCode.PARAM_ERROR.getMsg(),
                new BusinessException(UserErrorCode.PARAM_ERROR).getMessage());
    }

    @Test
    public void 子类同样不受影响() {
        assertEquals("脚本炸了", new EngineScriptException("脚本炸了", "第 3 行").getMessage());
    }
}
