package solvela.enums;

/**
 * 券类型：{@code t_member_coupon.coupon_type}，按枚举名存，JSON 也是枚举名。
 *
 * <p>今天只有一种：发券时一律写 {@link #GENERAL}（CouponIssueService）。真正区分券的是券模板
 * （满减 / 折扣、抵积分 / 抵现金），不是这一列。
 */
public enum CouponTypeEnum {

    /** 通用券 */
    GENERAL,
}
