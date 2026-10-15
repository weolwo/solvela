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

    /**
     * 邮箱是<b>在一台不受信任的设备上新绑的</b>，还在 7 天冷静期内，暂时不能用来核身。
     *
     * <p>剩余时间见 {@link StepUpResult#retryAfterSeconds()}。
     *
     * <h3>🔴 为什么要有这一档</h3>
     * 首次绑定邮箱不要求任何额外验证，所以一个知道密码的人在新设备上可以
     * 「绑自己的邮箱 → 用它通过二次验证 → 把东西搬走」。
     * 而我们偏偏要给「没绑就去绑定」一个显眼的按钮（方案 §6.4）——
     * 按钮要给，这一档就必须一起上，否则那颗按钮就是把接管链铺成了主路。
     * 详见 {@code ContactCoolingStore}。
     */
    CONTACT_COOLING,

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
