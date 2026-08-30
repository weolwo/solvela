package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品支付方式，对齐 {@code t_mall_commodity.pay_type}。
 *
 * <p>⚠️ {@link #POINTS} 时 {@code cash_price} 恒为 0 —— 这是一条落库约束，
 * 保存商品时按 pay_type 校验，别只信前端传上来的价格。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum MallPayTypeEnum implements BaseEnum {

    POINTS(1, "纯积分"),

    POINTS_CASH(2, "积分+现金"),
    ;

    private final Integer value;

    private final String desc;
}
