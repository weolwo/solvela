package solvela.member.api;

/**
 * 重置密码的结果。
 *
 * @param reason            失败原因；成功时为 null
 * @param revokedSessions   成功时吊销掉的会话数。<b>要返回给用户看</b> ——
 *                          「已在 3 台设备上退出登录」是他判断「刚才是不是别人在动我账号」的依据
 * @param retryAfterSeconds 还要等多久才能再试，仅 {@link PasswordResetFailReason#TOO_MANY_ATTEMPTS}
 *                          时有意义。<b>给的是秒数，不是「请 10 分钟后重试」那句话</b>
 */
public record MemberPasswordResetResult(PasswordResetFailReason reason, int revokedSessions,
                                        long retryAfterSeconds) {

    public boolean success() {
        return reason == null;
    }

    public static MemberPasswordResetResult ok(int revokedSessions) {
        return new MemberPasswordResetResult(null, revokedSessions, 0L);
    }

    public static MemberPasswordResetResult fail(PasswordResetFailReason reason) {
        return new MemberPasswordResetResult(reason, 0, 0L);
    }

    /** 被限频挡下。<b>一定要带秒数</b>：走到这一步的人已经登不进去了，见那一档的注释 */
    public static MemberPasswordResetResult limited(long retryAfterSeconds) {
        return new MemberPasswordResetResult(
                PasswordResetFailReason.TOO_MANY_ATTEMPTS, 0, retryAfterSeconds);
    }
}
