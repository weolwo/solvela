package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 核身通道：这一次要把验证码<b>发到哪里去</b>。取值与 {@code t_member_risk_event.verify_channel} 一致。
 *
 * <h3>为什么它不等于登录方式</h3>
 * 「用手机号登录」和「把码发到手机上」是两件事，而方案 §3.7 的核身路由恰恰要让它们分开：
 * <ul>
 *   <li>{@code OTP_STRONG} 要求<b>换一个通道</b> —— 手机号登录时发到邮箱。
 *       意义在于避开可能已经失守的那个因子：密码复用的人，本站密码和邮箱密码往往一起泄露，
 *       往同一个邮箱发码证明不了任何事；</li>
 *   <li>强通道不可用时要能<b>退回</b>到另一条 —— 今天短信厂商还没接，手机号账号的 OTP
 *       只能走邮箱。按登录方式硬编码的话，这种退回在类型上就表达不出来。</li>
 * </ul>
 *
 * <p>所以通道由路由选定，<b>写进凭票</b>，发码那一步只看票 —— 不再看登录方式，也不信客户端。
 *
 * @Date 2026-10-16
 */
@Getter
@AllArgsConstructor
public enum MemberVerifyChannelEnum implements BaseEnum {

    EMAIL(1, "邮箱"),

    SMS(2, "短信"),
    ;

    private final Integer value;

    private final String desc;
}
