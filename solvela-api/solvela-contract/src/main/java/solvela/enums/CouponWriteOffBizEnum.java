package solvela.enums;

/**
 * 券核销流水的业务类型：{@code t_coupon_write_off.biz_type}，也是 {@code CouponLockCmd} / {@code CouponWriteOffCmd} 的 bizType。
 * 按枚举名存，JSON 也是枚举名。
 */
public enum CouponWriteOffBizEnum {

    /** 商城订单 */
    MALL,

    /** 外部场景（充话费等），此时 scene_code 有值 */
    EXTERNAL,

    /** 卡在锁定态的券被定时任务退回（CouponStuckLockReleaseJob） */
    BOUNCE,
}
