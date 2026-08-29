package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员券状态，对齐 {@code t_member_coupon.status}。
 *
 * <p>⚠️ {@link #EXPIRED} 与 {@link #VOIDED} 要分开：前者是时间到了自然失效，
 * 后者是运营主动作废（发错了、活动取消）。对用户都是「用不了」，对运营是两回事 ——
 * 作废量突然变大意味着有人在批量撤券，那是要问一句的。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum CouponStatusEnum implements BaseEnum {

    UNUSED(0, "未使用"),

    USED(1, "已使用"),

    /**
     * 已过期：时间到了自然失效
     */
    EXPIRED(2, "已过期"),

    /**
     * 已作废：运营主动撤销
     */
    VOIDED(3, "已作废"),
    ;

    private final Integer value;

    private final String desc;
}
