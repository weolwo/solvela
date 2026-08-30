package solvela.member.api;

/**
 * 认证结果。成功带身份，失败带原因。
 *
 * <p>不用 {@code Optional} 也不用抛异常：调用方必须同时拿到「成不成」和「为什么」，
 * 而这两件事在跨进程之后只能靠返回值传递。
 *
 * @param identity      成功时的会员身份；失败时为 null
 * @param reason        失败原因；成功时为 null
 * @param lockedSeconds 被限制的剩余秒数，仅 {@link AuthFailReason#OPERATION_LIMITED} 时有意义。
 *                      <b>给的是秒数这个事实，不是「请 3 分钟后重试」这句话</b> ——
 *                      向上取整到分钟、要不要加一句「联系客服」，都是展示层的决定
 */
public record MemberAuthResult(MemberIdentity identity, AuthFailReason reason, long lockedSeconds) {

    public boolean success() {
        return reason == null;
    }

    public static MemberAuthResult ok(MemberIdentity identity) {
        return new MemberAuthResult(identity, null, 0L);
    }

    public static MemberAuthResult fail(AuthFailReason reason) {
        return new MemberAuthResult(null, reason, 0L);
    }

    public static MemberAuthResult limited(long lockedSeconds) {
        return new MemberAuthResult(null, AuthFailReason.OPERATION_LIMITED, lockedSeconds);
    }
}
