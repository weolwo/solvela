package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次登录尝试<b>最后怎么样了</b>。取值与 {@code t_member_risk_event.outcome} 一致。
 *
 * <p>与「风控判了什么」（{@link MemberRiskDecisionEnum}）分开记：风控说放行、结果密码错，
 * 和风控说要 OTP、结果用户没回来验码，是两件不同的事，误伤分析要能分开看（方案 §8.2）。
 *
 * <p>⚠️ 几档是按登录起的名字，注册（2026-10-16 接进同一个引擎）复用它们：
 * 验证码不对记 {@link #BAD_CREDENTIALS}、被限流记 {@link #PRE_BLOCKED}、
 * 身份被占用记 {@link #IDENTITY_TAKEN}。
 * 不另起一套注册专用的结局，是因为那会让看板上每一处统计都要分两种口径去写。
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

    /**
     * 这次尝试<b>以一个未预料的异常收场</b>（对用户就是 500）。
     *
     * <p>🔴 它存在的唯一理由是守住「每一次登录尝试都有一行流水」这个不变量：
     * {@code RiskSession} 在 {@code close()} 里兜底落库，而那时 outcome 还没被任何人设过。
     * 没有这一档的话，异常路径就<b>恰好</b>没有流水 —— 而「登录报 500 时风控判了什么、
     * 命中了哪几条规则」正是最想知道的那一刻。
     *
     * <p>风控引擎自己是不会走到这里的（它整体 fail-open，见方案 §3.9）。走到这里说明
     * 查会员、验密码、写登录日志这些主干环节出了问题。
     */
    INTERNAL_ERROR(13, "内部异常"),

    /**
     * 注册时身份已被占用（手机号 / 邮箱）。
     *
     * <h3>🔴 它不能并进 {@link #BAD_CREDENTIALS}</h3>
     * 「这个号已经有人了」是注册场景里<b>信号最强</b>的一个结局：真人一辈子撞到一两次，
     * 而一个拿着泄露号码库挨个试「哪些号已经注册过」的脚本，产出的几乎全是这一档。
     * 并进「凭据错」的话，这个形状在看板上就看不见了 —— 而注册这边本来就只有
     * {@code PHONE_TAKEN / EMAIL_TAKEN} 这一个藏不掉的枚举口子（见 {@code RegisterFailReason}）。
     */
    IDENTITY_TAKEN(14, "身份已被注册"),
    ;

    private final Integer value;

    private final String desc;
}
