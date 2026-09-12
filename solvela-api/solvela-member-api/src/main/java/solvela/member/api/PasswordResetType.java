package solvela.member.api;

/**
 * 用哪条通道找回密码。
 *
 * <p>命名与 {@link MemberLoginType} / {@link MemberRegisterType} 一个路数：
 * 一个 {@code identity} 字段 + 一个类型枚举，而不是并排两个
 * {@code email} / {@code phone} 字段。理由在 {@code MemberLoginRequest}
 * 的注释里写过 —— 「继续叫 email 但有时候放的是手机号」是一个迟早会骗到人的字段名。
 */
public enum PasswordResetType {

    /** 邮箱验证码。 */
    EMAIL_CODE,

    /**
     * 短信验证码。
     *
     * <p>⚠️ 只有<b>绑过手机号</b>的账号能走这条。邮箱注册且没绑手机的会员
     * 走 {@link #EMAIL_CODE}，两条都没有的账号自助找不回来 —— 那种情况只能找客服，
     * 而界面上要如实说出来，不能让人填半天再撞一句「账号不存在」。
     */
    SMS_CODE,
}
