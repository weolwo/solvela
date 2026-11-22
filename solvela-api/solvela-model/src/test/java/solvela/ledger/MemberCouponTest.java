package solvela.ledger;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.CouponStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberCouponTest {

    @Test
    @DisplayName("发券落为未使用")
    void 发券() {
        MemberCoupon coupon = new MemberCoupon();
        coupon.issue();
        assertEquals(CouponStatusEnum.UNUSED, coupon.getStatus());
    }

    @Test
    @DisplayName("已经发过的券不能再发一次")
    void 不能重复发() {
        MemberCoupon coupon = new MemberCoupon();
        coupon.issue();
        assertThrows(IllegalStateException.class, coupon::issue);
    }

    /**
     * 🔴 status 去掉了 setter，读库时 MyBatis 必须还能把它填进来。
     * 自动映射先问 {@code hasSetter}，为假就<b>静默跳过</b>这一列 —— 表现是券包里的券状态全是 null，
     * 核销服务的幂等判断（「已经是我锁的」）全部走错分支。这里走的是结果映射用的同一套反射，不连库。
     */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        MemberCoupon coupon = new MemberCoupon();
        MetaObject meta = SystemMetaObject.forObject(coupon);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", CouponStatusEnum.LOCKED);
        assertEquals(CouponStatusEnum.LOCKED, coupon.getStatus());
    }
}
