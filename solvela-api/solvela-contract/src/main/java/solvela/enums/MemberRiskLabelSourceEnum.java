package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标注是<b>谁</b>给的。取值与 {@code t_member_risk_event.label_source} 一致。
 *
 * <h3>🔴 必须和 {@link MemberRiskLabelEnum} 一起记，而且统计时要分开算</h3>
 * 三个来源的可信度差一个数量级：
 * <ul>
 *   <li>{@link #USER_REPORT} —— 用户自己说「这不是我」，最强；</li>
 *   <li>{@link #OLD_DEVICE_CONFIRM} —— 在<b>另一台受信任设备</b>上点了「是我」。较强，
 *       但那台设备也可能就在盗号者手上；</li>
 *   <li>{@link #SUPPORT} —— 客服人工判的，取决于客服拿到了什么证据。</li>
 * </ul>
 * 混在一起算的话，「确认本人」这个数会被最弱的那一类撑起来，而阈值正是按它调的。
 *
 * <p>⚠️ 「OTP 通过」刻意<b>不</b>写进标注（方案 §8.1）：盗号者拿到验证码也能验过，
 * 把它算成「确认本人」会系统性地低估漏过率。它作为一个独立指标单独统计。
 *
 * @Date 2026-10-16
 */
@Getter
@AllArgsConstructor
public enum MemberRiskLabelSourceEnum implements BaseEnum {

    /** 用户在「我的登录设备」里点了「这不是我」 */
    USER_REPORT(1, "用户举报"),

    /** 在另一台受信任设备上回答了「是我」 */
    OLD_DEVICE_CONFIRM(2, "老设备确认"),

    SUPPORT(3, "客服标注"),
    ;

    private final Integer value;

    private final String desc;
}
