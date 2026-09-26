package solvela.member.api;

/**
 * 一台设备对一个会员而言<b>受不受信任</b>，以及凭什么。
 *
 * <h3>它回答的不是「这台设备可不可疑」</h3>
 * 那是 {@code DeviceGuard} 的事（量级异常、人工封禁）。这里问的是更窄的一件事：
 * <b>这个会员在这台设备上有没有「老交情」</b>。一台完全正常的新手机，对它的主人来说也是 {@link #NEW_DEVICE}。
 *
 * <p>所以它只用来决定「要不要多验一道」，不用来拦人、不进风控评分 ——
 * 新设备不是坏设备，只是信息不足。
 *
 * @Date 2026-09-26
 */
public enum DeviceTrust {

    /** 在这台设备上通过过一次二次验证。 */
    VERIFIED(true),

    /**
     * 这台设备上该会员的成功登录早于信任门槛（默认 7 天）。
     *
     * <p>这一档让<b>存量老用户在常用设备上零打扰</b>。代价是一个潜伏够久的盗号者也能拿到它 ——
     * 那是门槛天数本身的取舍，见 {@code MemberStepUpProperties#trustAfter}。
     */
    HISTORY(true),

    /** 这台设备对该会员是新的。 */
    NEW_DEVICE(false),

    /**
     * 请求没有可信设备身份（老客户端、设备注册失败）。
     *
     * <p>单列出来而不是并进 {@link #NEW_DEVICE}：没有设备号就<b>没有地方记住</b>「验证过了」，
     * 验一次、下一次还要验，会变成死循环。调用方要据此让客户端先补上设备身份，而不是弹验证码。
     */
    NO_DEVICE(false),
    ;

    private final boolean trusted;

    DeviceTrust(boolean trusted) {
        this.trusted = trusted;
    }

    public boolean trusted() {
        return trusted;
    }
}
