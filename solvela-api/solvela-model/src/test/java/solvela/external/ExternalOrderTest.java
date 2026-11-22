package solvela.external;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.ExternalOrderStatusEnum;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalOrderTest {

    private static final LocalDateTime EXPIRE = LocalDateTime.of(2026, 11, 22, 10, 15);

    @Test
    @DisplayName("落单为待支付，带超时时间")
    void 落单待支付() {
        ExternalOrder order = new ExternalOrder();
        order.awaitPayment(EXPIRE);
        assertEquals(ExternalOrderStatusEnum.UNPAID, order.getStatus());
        assertEquals(EXPIRE, order.getExpireTime());
    }

    @Test
    @DisplayName("已经定过状态的单不能再定一次")
    void 不能重复定初始状态() {
        ExternalOrder order = new ExternalOrder();
        order.awaitPayment(EXPIRE);
        assertThrows(IllegalStateException.class, () -> order.awaitPayment(EXPIRE));
    }

    /**
     * 🔴 status 去掉了 setter，读库时 MyBatis 必须还能把它填进来。
     * 自动映射先问 {@code hasSetter}，为假就<b>静默跳过</b>这一列 —— 表现是查出来的单子状态全是 null，
     * C 端「能不能支付」的按钮全部消失。这里走的是结果映射用的同一套反射，不连库。
     */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        ExternalOrder order = new ExternalOrder();
        MetaObject meta = SystemMetaObject.forObject(order);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", ExternalOrderStatusEnum.SUCCESS);
        assertEquals(ExternalOrderStatusEnum.SUCCESS, order.getStatus());
    }
}
