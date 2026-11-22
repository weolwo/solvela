package solvela.mall;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MallOrderStatusEnum;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 10, 0);

    @Test
    @DisplayName("积分+现金单落成待支付，带超时时间，不带支付时间")
    void 落单待支付() {
        MallOrder order = new MallOrder();
        order.awaitPayment(NOW.plusMinutes(15));
        assertAll(
                () -> assertEquals(MallOrderStatusEnum.UNPAID, order.getStatus()),
                () -> assertEquals(NOW.plusMinutes(15), order.getExpireTime()),
                () -> assertNull(order.getPayTime()));
    }

    @Test
    @DisplayName("纯积分单落成待履约，带支付时间，不带超时时间")
    void 落单即结清() {
        MallOrder order = new MallOrder();
        order.settleOnCreate(NOW);
        assertAll(
                () -> assertEquals(MallOrderStatusEnum.PENDING, order.getStatus()),
                () -> assertEquals(NOW, order.getPayTime()),
                () -> assertNull(order.getExpireTime()));
    }

    @Test
    @DisplayName("已经定过状态的订单不能再定一次")
    void 不能重复定初始状态() {
        MallOrder order = new MallOrder();
        order.settleOnCreate(NOW);
        assertThrows(IllegalStateException.class, () -> order.awaitPayment(NOW));
        assertThrows(IllegalStateException.class, () -> order.settleOnCreate(NOW));
    }

    /**
     * 🔴 status 去掉了 setter，读库时 MyBatis 必须还能把它填进来。
     * 自动映射先问 {@code hasSetter}，为假就<b>静默跳过</b>这一列 —— 表现是查出来的订单状态全是 null。
     * 这里走的是结果映射用的同一套反射（{@link MetaObject}），不连库。
     */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        MallOrder order = new MallOrder();
        MetaObject meta = SystemMetaObject.forObject(order);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", MallOrderStatusEnum.FINISHED);
        assertEquals(MallOrderStatusEnum.FINISHED, order.getStatus());
    }
}
