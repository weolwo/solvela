package solvela.member.api;

/**
 * 注册结果。成功带身份，失败带原因 —— 形状与 {@link MemberAuthResult} 刻意保持一致。
 *
 * <h3>成功时返回的是 {@link MemberIdentity}，不是「注册成功」四个字</h3>
 * 网关拿到它就能直接签令牌让用户进去，不必再走一次登录 ——
 * 「注册完还要自己登一次」是纯粹的多余步骤，而多一次登录调用就多一次失败的机会。
 *
 * <p>身份里没有手机号也没有密码，理由见 {@link MemberIdentity} 的类注释。
 *
 * @param identity           成功时的会员身份；失败时为 null
 * @param reason             失败原因；成功时为 null
 * @param retryAfterSeconds  还要等多久才能再试，仅 {@link RegisterFailReason#TOO_MANY_ATTEMPTS}
 *                           时有意义。<b>给的是秒数，不是「请 10 分钟后重试」这句话</b> ——
 *                           怎么说是展示层的决定
 */
public record MemberRegisterResult(
        MemberIdentity identity,
        RegisterFailReason reason,
        long retryAfterSeconds) {

    public boolean success() {
        return reason == null;
    }

    public static MemberRegisterResult ok(MemberIdentity identity) {
        return new MemberRegisterResult(identity, null, 0L);
    }

    public static MemberRegisterResult fail(RegisterFailReason reason) {
        return new MemberRegisterResult(null, reason, 0L);
    }

    public static MemberRegisterResult tooManyAttempts(long retryAfterSeconds) {
        return new MemberRegisterResult(null, RegisterFailReason.TOO_MANY_ATTEMPTS, retryAfterSeconds);
    }
}
