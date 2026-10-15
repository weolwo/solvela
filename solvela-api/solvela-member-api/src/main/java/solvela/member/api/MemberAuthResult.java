package solvela.member.api;

import solvela.enums.MemberVerifyChannelEnum;

/**
 * 认证结果。成功带身份，失败带原因。
 *
 * <p>不用 {@code Optional} 也不用抛异常：调用方必须同时拿到「成不成」和「为什么」，
 * 而这两件事在跨进程之后只能靠返回值传递。
 *
 * <h3>🔴 这里只放【事实】，不放话术，也不放 UI</h3>
 * 剩余秒数、还剩几次、码发到哪条通道 —— 都是事实。
 * 「请 5 分钟后再试」「要不要给他一个找回密码的按钮」是接入层的决定，
 * 写进域的返回值里，App 和后台就只能共用同一句话了（见方案 §6.4 的约定 2）。
 *
 * @param identity               成功时的会员身份；失败时为 null
 * @param reason                 失败原因；成功时为 null
 * @param lockedSeconds          被限制的剩余秒数，{@link AuthFailReason#OPERATION_LIMITED}、
 *                               {@link AuthFailReason#DEVICE_LIMITED}、{@link AuthFailReason#IP_LIMITED} 与
 *                               {@link AuthFailReason#PASSWORD_COOLDOWN} 时有意义。
 *                               <b>给的是秒数这个事实，不是「请 3 分钟后重试」这句话</b> ——
 *                               向上取整到分钟、要不要加一句「联系客服」，都是展示层的决定
 * @param challengeTicket        仅 {@link AuthFailReason#DEVICE_VERIFICATION_REQUIRED} 时有值：
 *                               密码已验过、这台设备要多验一道时发的一次性凭票。之后的发码、验码都凭它进行，
 *                               见 {@link MemberAuthApi#sendChallengeCode} / {@link MemberAuthApi#verifyChallenge}
 * @param verifyChannel          仅 {@link AuthFailReason#DEVICE_VERIFICATION_REQUIRED} 时有值：
 *                               核身路由选定的通道（方案 §3.7）。客户端据它说「验证码已发到你的邮箱 / 手机」——
 *                               说错了用户会去翻另一个收件箱，然后以为我们没发
 * @param attemptsBeforeCooldown 仅 {@link AuthFailReason#BAD_CREDENTIALS} 时<b>可能</b>有值：
 *                               再错几次就会进密码冷却。
 *                               <p>🔴 只在<b>剩 1 次</b>这种临界时刻才给（由接入层决定给不给、怎么说）。
 *                               一直给等于把阶梯的形状完整地告诉对面，而它对正常用户的价值只在最后那一下 ——
 *                               「再错一次就要等一分钟」能让人停下来想想，「还能错 4 次」只会让他继续猜。
 *                               <p>它<b>不构成账号枚举</b>：阶梯按身份摘要计数，没有账号的身份算出同样的数
 */
public record MemberAuthResult(MemberIdentity identity, AuthFailReason reason, long lockedSeconds,
                               String challengeTicket, MemberVerifyChannelEnum verifyChannel,
                               Integer attemptsBeforeCooldown) {

    public boolean success() {
        return reason == null;
    }

    public static MemberAuthResult ok(MemberIdentity identity) {
        return new MemberAuthResult(identity, null, 0L, null, null, null);
    }

    public static MemberAuthResult fail(AuthFailReason reason) {
        return new MemberAuthResult(null, reason, 0L, null, null, null);
    }

    /**
     * 凭据错。
     *
     * @param attemptsBeforeCooldown 再错几次进冷却；算不出来（验证码登录、没有摘要）时传 null
     */
    public static MemberAuthResult badCredentials(Integer attemptsBeforeCooldown) {
        return new MemberAuthResult(null, AuthFailReason.BAD_CREDENTIALS, 0L, null, null,
                attemptsBeforeCooldown);
    }

    public static MemberAuthResult limited(long lockedSeconds) {
        return new MemberAuthResult(null, AuthFailReason.OPERATION_LIMITED, lockedSeconds, null, null, null);
    }

    public static MemberAuthResult deviceLimited(long lockedSeconds) {
        return new MemberAuthResult(null, AuthFailReason.DEVICE_LIMITED, lockedSeconds, null, null, null);
    }

    public static MemberAuthResult ipLimited(long lockedSeconds) {
        return new MemberAuthResult(null, AuthFailReason.IP_LIMITED, lockedSeconds, null, null, null);
    }

    /**
     * 密码这条通道在阶梯冷却中。<b>与 {@link #limited} 分开</b>：用户此刻还有两条路可走
     * （验证码登录、找回密码），接入层要据此给出按钮，见 {@link AuthFailReason#PASSWORD_COOLDOWN}。
     */
    public static MemberAuthResult passwordCooldown(long lockedSeconds) {
        return new MemberAuthResult(null, AuthFailReason.PASSWORD_COOLDOWN, lockedSeconds, null, null, null);
    }

    /** 密码对了，这台设备要多验一道：凭这张票去发码、验码，码走 {@code channel} 这条通道 */
    public static MemberAuthResult challenge(String ticket, MemberVerifyChannelEnum channel) {
        return new MemberAuthResult(null, AuthFailReason.DEVICE_VERIFICATION_REQUIRED, 0L, ticket, channel, null);
    }

    /**
     * 要先过一次滑块。<b>不是失败</b>，见 {@link AuthFailReason#CAPTCHA_REQUIRED} ——
     * 密码还没验，客户端拖完图原样重试即可。
     */
    public static MemberAuthResult captchaRequired() {
        return new MemberAuthResult(null, AuthFailReason.CAPTCHA_REQUIRED, 0L, null, null, null);
    }
}
