package solvela.member.api;

/**
 * 注册失败的原因。与 {@link AuthFailReason} 分开，因为两边该告诉用户多少<b>正好相反</b>。
 *
 * <h3>登录要含糊，注册必须明说</h3>
 * 登录时「查无此人」和「密码错」合并成一个 {@link AuthFailReason#BAD_CREDENTIALS}，
 * 为的是不让人拿登录接口枚举手机号。
 *
 * <p>注册反过来：{@link #PHONE_TAKEN} <b>必须</b>如实告诉用户，否则他不知道该去登录还是该换号，
 * 只会一直点注册。这确实构成一个手机号枚举口子 —— 但它是注册这件事本身自带的，
 * 藏不掉（藏了用户就没法用），只能靠 {@link #TOO_MANY_ATTEMPTS} 的限频把速率压下去。
 *
 * <p>🔴 所以别为了「安全」把 PHONE_TAKEN 改成含糊措辞：那不会减少泄露，
 * 只会让真实用户卡在注册页，而攻击者照样能从「注册成功与否」推出同样的信息。
 */
public enum RegisterFailReason {

    /**
     * 手机号格式不对。可以明说：一个非法的串本来就不可能是任何人的手机号，不泄露任何信息。
     */
    BAD_PHONE_FORMAT,

    /**
     * 手机号已注册。见类注释：必须明说。
     *
     * <p>注意「已注销」的账号<b>不算</b>已注册 —— 注销会把 {@code phone_hash} 置 NULL 释放号码，
     * 所以本人能用同一个号重新注册，这是 DDL 就设计好的。
     */
    PHONE_TAKEN,

    /**
     * 密码不满足强度要求。具体规则见会员域的 {@code MemberPasswordPolicy}，
     * <b>只有那一处</b> —— 前端只展示提示文案，不重写一遍规则。
     */
    WEAK_PASSWORD,

    /**
     * 同一 IP 短时间内注册请求过多。剩余秒数见 {@link MemberRegisterResult#retryAfterSeconds()}。
     *
     * <p>⚠️ 这是<b>目前唯一</b>拦批量注册的东西 —— 本项目还没有短信验证码。
     */
    TOO_MANY_ATTEMPTS
}
