package solvela.enums;

import lombok.Getter;

/**
 * 券抵扣的是什么，对齐 {@code t_coupon_template.deduct_target}。
 *
 * <h3>🔴 两种券不可比，不要替用户跨类选「最优」</h3>
 * 一张减积分的券和一张减现金的券，「谁更划算」系统答不了 ——
 * 1 积分 ≠ 1 元，而汇率是业务定义、还会变。
 *
 * <p>所以试算的输出是<b>按本枚举分组</b>的：每组内推荐最优的那张，
 * 跨组让用户自己挑。硬给一个「全局最优」，就是替用户做了一个
 * 系统没有依据的决定。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Getter
public enum CouponDeductTargetEnum {

    /**
     * 抵扣现金。
     *
     * <p>⚠️ 这一档<b>依赖支付链路</b>，而全仓至今没有任何支付回调代码
     *（{@code MallRedeemService} 自己的注释写着「支付链路至今一行代码都没有」）。
     * 所以 CASH 券要等假支付做完才能真正用起来，见方案 §6.3。
     */
    CASH("现金"),

    /**
     * 抵扣积分。
     *
     * <p>不依赖支付 —— <b>第一个能真正跑通的闭环就是它</b>（商城纯积分商品）。
     */
    SCORE("积分"),
    ;

    private final String desc;

    CouponDeductTargetEnum(String desc) {
        this.desc = desc;
    }
}
