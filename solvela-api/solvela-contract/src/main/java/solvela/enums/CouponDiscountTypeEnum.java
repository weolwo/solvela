package solvela.enums;

import lombok.Getter;

/**
 * 券的抵扣方式，对齐 {@code t_coupon_template.discount_type} 与
 * {@code t_member_coupon.discount_type}（后者是发券时的快照）。
 *
 * <p>只有两种，而且<b>不打算再加</b>：券的复杂度一旦从「减多少」跑到
 * 「怎么算」，下一步就是规则引擎，而这个项目的券维度就那四五个 ——
 * 上引擎是把「改一行配置」变成「改一段代码」。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Getter
public enum CouponDiscountTypeEnum {

    /** 固定金额：{@code discount_value} 就是减多少 */
    FIXED("固定金额"),

    /**
     * 百分比：{@code discount_value} 是折扣率（20 表示减 20%）。
     *
     * <p>🔴 这一档的 {@code max_discount} <b>必填</b>。不设上限的「8 折」
     * 碰上一台 iPhone 就是一次资损，而且不会报错、只会少收钱。
     * 校验在 {@code CouponTemplateService.save} 里。
     */
    PERCENT("百分比"),
    ;

    private final String desc;

    CouponDiscountTypeEnum(String desc) {
        this.desc = desc;
    }
}
