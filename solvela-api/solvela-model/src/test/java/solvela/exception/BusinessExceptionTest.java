package solvela.exception;

import solvela.code.UserErrorCode;
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
    public void 带错误码的构造既保留文案也保留身份() {
        BusinessException e = new BusinessException(UserErrorCode.DATA_NOT_EXIST, "订单不存在");
        assertEquals("订单不存在", e.getMessage(), "自定义文案应覆盖错误码自带的那句");
        assertSame(UserErrorCode.DATA_NOT_EXIST, e.getErrorCode(), "错误码决定 HTTP 状态与响应体里的 code");
    }

    @Test
    public void 不指定错误码时默认是用户级参数错误() {
        // 默认值不是随手选的：业务校验失败是「用户干了不该干的事」，不是系统故障。
        // 默认成 SYSTEM_ERROR 会让「运营重复点了一次删除」和「数据库连不上」
        // 落在同一条告警曲线上
        assertSame(UserErrorCode.PARAM_ERROR, new BusinessException("库存不足").getErrorCode());
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
