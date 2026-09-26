package solvela.member.api;

/**
 * 二次验证失败的原因。域只说原因，<b>措辞由调用方定</b>。
 *
 * @Date 2026-09-26
 */
public enum StepUpFailReason {

    /** 请求没有可信设备身份，验证通过了也没处记住。见 {@link DeviceTrust#NO_DEVICE}。 */
    NO_DEVICE,

    /**
     * 会员没有绑定邮箱，收不到码。
     *
     * <p>生产只开放邮箱注册，正常走不到；留着是因为手机号注册那条路在 dev / test 是通的。
     */
    NO_EMAIL,

    /** 上一封发出去还不到冷却时间。 */
    TOO_FREQUENT,

    /** 今天发得太多了。 */
    DAILY_LIMIT_REACHED,

    /** 邮件发不出去 —— 我们自己的问题。 */
    SEND_FAILED,

    /** 没有待校验的码：没发过，或已过期。 */
    CODE_EXPIRED,

    /** 码不对。 */
    CODE_MISMATCH,

    /** 连续输错次数用尽，码已作废，要重新获取。 */
    CODE_LOCKED,
}
