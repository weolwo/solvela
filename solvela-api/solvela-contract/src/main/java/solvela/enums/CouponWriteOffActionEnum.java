package solvela.enums;

import lombok.Getter;

/**
 * 核销流水的动作，对齐 {@code t_coupon_write_off.action}。
 *
 * <h3>🔴 三个动作都记流水，不是只记核销成功那一次</h3>
 * 只记 {@link #CONFIRM} 的话行数少一半，但「锁了又释放」就查不到了 ——
 * 而券的纠纷恰恰大多发生在那个窗口里：用户说「我的券刚才还能用，
 * 现在怎么回事」，没有流水就查无对证。
 *
 * <p>量级完全撑得住：百万张券、三成核销率也就三十万次 × 3 行，
 * 和 {@code t_member_notification} 那张亿级表不是一个数量级。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Getter
public enum CouponWriteOffActionEnum {

    /** 锁定：0-未使用 → 4-锁定中。提交订单时 */
    LOCK("锁定"),

    /** 核销：4-锁定中 → 1-已使用。支付 / 履约成功 */
    CONFIRM("核销"),

    /** 释放：4-锁定中 → 0-未使用。订单取消、支付超时、履约失败 */
    RELEASE("释放"),
    ;

    private final String desc;

    CouponWriteOffActionEnum(String desc) {
        this.desc = desc;
    }
}
