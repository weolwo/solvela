package solvela.enums;

import lombok.Getter;

/**
 * 券的适用范围，对齐 {@code t_coupon_template.scope_type}。
 *
 * <h3>🔴 为什么有 CATEGORY 而不是只有 COMMODITY</h3>
 * 绑商品 id 列表的话，每上一个新商品就要回头改所有相关的券 —— 那是维护地狱，
 * 而且漏改不报错，只是那张券在新商品上莫名其妙用不了。
 * 绑类目（{@code t_mall_category} 是带 parent_id 的树）之后，
 * 新商品挂进类目就自动进范围。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Getter
public enum CouponScopeTypeEnum {

    /** 全场可用。{@code scope_refs} 为空 */
    ALL("全场通用"),

    /** 指定商品。{@code scope_refs} 是 commodity_id 数组 */
    COMMODITY("指定商品"),

    /** 指定类目。{@code scope_refs} 是 category_id 数组，<b>含子类目</b> */
    CATEGORY("指定类目"),

    /**
     * 外部场景（充话费这类）。{@code scope_refs} 是场景码数组。
     *
     * <p>这一档让券能被商城之外的东西消费掉 —— 核销 API 是同一套，
     * 只是换一个 scene。充话费是第一个要接它的场景。
     */
    EXTERNAL("外部场景"),
    ;

    private final String desc;

    CouponScopeTypeEnum(String desc) {
        this.desc = desc;
    }
}
