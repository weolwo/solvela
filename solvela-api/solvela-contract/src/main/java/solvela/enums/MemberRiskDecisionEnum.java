package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录风控的处置等级。取值与 {@code t_member_risk_event.decision}、{@code t_member_risk_rule.direct_decision}
 * 以及分级规则 {@code params.tiers[].do} 一一对应。
 *
 * <h3>🔴 取值就是严厉程度</h3>
 * 多条规则同时命中时取最严的那一级（方案 §3.2），实现上就是取 value 最大的那个 ——
 * 所以<b>新增一档只能插在它该在的严厉程度上</b>，不能往末尾追加一个「其实不那么严」的值。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskDecisionEnum implements BaseEnum {

    PASS(1, "放行"),

    /** 前置：要滑块。客户端已经会处理 {@code CAPTCHA_REQUIRED} */
    CAPTCHA(2, "滑块"),

    /** 后置：要验证码（凭票二次验证） */
    OTP(3, "OTP"),

    /** 后置：要换一个通道的验证码 —— 避开可能已经和密码一起失守的那个因子 */
    OTP_STRONG(4, "换通道OTP"),

    /** 前置：密码阶梯冷却。只锁密码通道，验证码登录与找回密码不受影响 */
    COOLDOWN(5, "密码冷却"),

    LIMIT(6, "限流"),

    /** 对外与 {@link #LIMIT} 同一句文案：不告诉对方「你被拉黑了」 */
    DENY(7, "拒绝"),
    ;

    private final Integer value;

    private final String desc;

    /** 两者取更严的那个；null 当作不存在 */
    public static MemberRiskDecisionEnum max(MemberRiskDecisionEnum a, MemberRiskDecisionEnum b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.value >= b.value ? a : b;
    }

    public boolean atLeast(MemberRiskDecisionEnum other) {
        return this.value >= other.value;
    }

    public static MemberRiskDecisionEnum ofValue(int value) {
        for (MemberRiskDecisionEnum d : values()) {
            if (d.value == value) {
                return d;
            }
        }
        throw new IllegalArgumentException("未知的风控处置: " + value);
    }
}
