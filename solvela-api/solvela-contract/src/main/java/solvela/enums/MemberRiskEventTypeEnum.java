package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控事件类型。取值与 {@code t_member_risk_rule.event_type}、{@code t_member_risk_event.event_type} 一致。
 *
 * <h3>它同时是「规则属于哪个场景」</h3>
 * 一条规则只属于一个场景（{@code t_member_risk_rule.event_type}），而且<b>以代码为准、与表核对</b>：
 * 把密码冷却那条规则在表里改成注册场景，它会在一个压根没有密码的流程里执行 ——
 * 所以不一致时启动即失败，与「阶段」同一个做法（见 {@code RiskRuleRegistry.toConfig}）。
 *
 * <p>要让同一种判断覆盖两个场景（比如 IP 黑名单），就写两条规则、两行配置 ——
 * 🔴 <b>刻意不做「一条规则适用多个场景」</b>：那会让一次改阈值同时影响登录和注册，
 * 而两边的量级、误伤面、可接受的处置完全不同。两行配置看着啰嗦，但它让「改这个数会影响谁」
 * 在表上一眼看得见。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskEventTypeEnum implements BaseEnum {

    LOGIN(1, "登录"),

    /** 凭票验码。是另一个请求，单独一行流水，{@code ref_event_id} 指回签票的那次登录 */
    LOGIN_VERIFY(2, "登录二次验证"),

    /**
     * 注册。
     *
     * <h3>它只有前置一个决策点</h3>
     * 登录分前后两段是因为「密码对不对」把可用的信号分成了两半。注册没有这条分界线 ——
     * 验证码验过之后账号就建出来了，没有「验完再判一次」的位置。
     *
     * <h3>🔴 注册场景的处置只能是放行 / 限流 / 拒绝</h3>
     * 滑块和 OTP 在这里没有落点：滑块票由网关在<b>发码那一步</b>核销，
     * 而注册本身已经验过一次验证码了，再要一道 OTP 等于让同一个邮箱收两封信。
     * 所以注册规则的参数校验会拒绝配成滑块或 OTP（见各规则的 {@code validate}）。
     */
    REGISTER(3, "注册"),
    ;

    private final Integer value;

    private final String desc;
}
