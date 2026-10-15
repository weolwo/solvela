package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 决策流水的<b>事后标注</b>：这一次登录事后被认定成什么。取值与 {@code t_member_risk_event.label} 一致。
 *
 * <h3>它是整套风控唯一的「答案」</h3>
 * 规则给出的是猜测，标注才是事实。有了它，「打扰了多少本人」「漏过了多少盗号」
 * 才从感觉变成两个能跑 SQL 的数 —— 而没有它，调阈值只能靠猜（方案 §8）。
 *
 * <h3>🔴 两档的可信度完全不同，所以不能合并统计</h3>
 * {@link #STOLEN} 是用户主动举报的，强；{@link #OWNER} 可能来自「老设备上点了是我」，
 * 也可能来自 OTP 通过 —— 后者只是弱证据（主人验过码，但盗号者拿到验证码也能验过）。
 * 所以 {@code label_source} 必须一起记，统计时分开算。
 *
 * @Date 2026-10-16
 */
@Getter
@AllArgsConstructor
public enum MemberRiskLabelEnum implements BaseEnum {

    OWNER(1, "确认本人"),

    STOLEN(2, "确认被盗"),
    ;

    private final Integer value;

    private final String desc;
}
