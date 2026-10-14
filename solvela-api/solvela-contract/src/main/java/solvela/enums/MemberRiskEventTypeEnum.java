package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控事件类型。取值与 {@code t_member_risk_rule.event_type}、{@code t_member_risk_event.event_type} 一致。
 *
 * <p>今天只有登录这一个场景；注册、重置密码、换绑等在扩面（P3）时加进来。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskEventTypeEnum implements BaseEnum {

    LOGIN(1, "登录"),

    /** 凭票验码。是另一个请求，单独一行流水，{@code ref_event_id} 指回签票的那次登录 */
    LOGIN_VERIFY(2, "登录二次验证"),
    ;

    private final Integer value;

    private final String desc;
}
