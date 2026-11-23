package solvela.ledger;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.DeliveryStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalDeliveryTest {

    @Test
    @DisplayName("落为待发货；不能再定一次")
    void 落待发货() {
        PhysicalDelivery delivery = new PhysicalDelivery();
        delivery.awaitShipment();
        assertEquals(DeliveryStatusEnum.PENDING, delivery.getStatus());
        assertThrows(IllegalStateException.class, delivery::awaitShipment);
    }

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        PhysicalDelivery delivery = new PhysicalDelivery();
        MetaObject meta = SystemMetaObject.forObject(delivery);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", DeliveryStatusEnum.SIGNED);
        assertEquals(DeliveryStatusEnum.SIGNED, delivery.getStatus());
    }
}
