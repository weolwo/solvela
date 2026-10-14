package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次登录尝试<b>最后怎么样了</b>。取值与 {@code t_member_risk_event.outcome} 一致。
 *
 * <p>与「风控判了什么」（{@link MemberRiskDecisionEnum}）分开记：风控说放行、结果密码错，
 * 和风控说要 OTP、结果用户没回来验码，是两件不同的事，误伤分析要能分开看（方案 §8.2）。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskOutcomeEnum implements BaseEnum {

    SUCCESS(1, "登录成功"),

    BAD_CREDENTIALS(2, "凭据错误"),

    UNKNOWN_ACCOUNT(3, "查无此人"),

    FROZEN(4, "账号冻结"),

    COOLDOWN(5, "密码冷却 / 账号锁定中"),

    BAD_FORMAT(6, "格式非法"),

    CAPTCHA_REQUIRED(7, "需要滑块（没带票）"),

    PRE_BLOCKED(8, "被前置限流 / 拒绝"),

    POST_BLOCKED(9, "被后置限流 / 拒绝"),

    CHALLENGED(10, "已签二次验证凭票"),

    VERIFY_PASSED(11, "二次验证通过"),

    VERIFY_FAILED(12, "二次验证失败"),
    ;

    private final Integer value;

    private final String desc;
}
