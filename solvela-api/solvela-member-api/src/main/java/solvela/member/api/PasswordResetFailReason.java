package solvela.member.api;

/**
 * 重置密码失败的原因。
 */
public enum PasswordResetFailReason {

    /** 邮箱格式不对。可以明说。 */
    BAD_EMAIL_FORMAT,

    /** 验证码没有 / 已过期。 */
    EMAIL_CODE_EXPIRED,

    /** 验证码错误。 */
    EMAIL_CODE_MISMATCH,

    /** 验证码错太多次，已作废。 */
    EMAIL_CODE_LOCKED,

    /** 新密码不符合强度要求。文案从 {@link MemberPasswordPolicy#HINT} 取，不在这里再写一遍。 */
    WEAK_PASSWORD,

    // ------------------------------------------------------------------ 手机号找回（2026-09-10）

    /** 手机号格式不对。 */
    BAD_PHONE_FORMAT,

    /** 没有待校验的短信验证码：从没发过，或已过期。 */
    SMS_CODE_EXPIRED,

    /** 短信验证码错误。 */
    SMS_CODE_MISMATCH,

    /** 短信验证码连续输错次数用尽，已作废，必须重新发送。 */
    SMS_CODE_LOCKED,

    /**
     * 这个邮箱没有对应的会员。
     *
     * <h3>🔴 为什么这一档不构成账号枚举</h3>
     * 发码那一步对没有会员的邮箱<b>也存了码</b>（{@code MailDelivery.SUPPRESS}），
     * 而且那封信没寄出去 —— 也就是说要走到这一档，攻击者得先<b>猜中一个六位数</b>
     * （百万分之一，且只有 5 次机会）。在那之前他拿到的是与「有账号」逐字相同的
     * 验证码错误回答。
     */
    ACCOUNT_NOT_FOUND,

    /**
     * 账号被冻结或已注销。
     *
     * <p>🔴 <b>冻结的账号不许自助找回</b>。允许的话，风控封掉一个刷子账号之后，
     * 他改个密码就能继续用 —— 而冻结的本意正是让他用不了。
     * 走到这一档说明他已经证明自己拥有这个邮箱，所以如实告知、引导找客服，
     * 判据同 {@link AuthFailReason#ACCOUNT_FROZEN}。
     */
    ACCOUNT_UNAVAILABLE,
}
