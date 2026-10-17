package solvela.app.domain;

/**
 * 请求对象里字符串字段的长度上限（网关方案 §5.6）。{@code @Size(max = …)} 引用这里的常量。
 *
 * <h3>它管的是「一个字段能有多大」，不是业务格式</h3>
 * 没有上限的字段，一个请求就能塞进几百 KB，然后被原样传给下游、写进日志、拿去算哈希。
 * 业务格式（手机号是不是 11 位、密码强不强）仍由各自的校验负责 —— 这里只给一个宽松的天花板，
 * <b>宁大勿小</b>：比真实合法值还小的上限，会让真人拿到一句「过长」而不知道哪里错了。
 * 每个请求对象的每个字符串字段都必须有上限，由 {@code RequestBodyContractTest} 强制。
 *
 * @Date 2026-10-17
 */
public final class InputLimits {

    /** 手机号或邮箱。邮箱的 RFC 上限是 254 */
    public static final int IDENTITY_LENGTH = 254;

    /** 密码。新密码的规则是 8–32 位（{@code MemberPasswordPolicy}），这里只是天花板：老账号的密码可能比今天的规则长 */
    public static final int PASSWORD_LENGTH = 128;

    /** 短信 / 邮箱 / 二次验证码 */
    public static final int CODE_LENGTH = 16;

    /** 服务端签发、客户端原样带回的票据与编号：登录凭票、确认单号、会话号、设备号、滑块编号 */
    public static final int TOKEN_LENGTH = 128;

    /** 客户端生成的幂等键（UUID 是 36 位） */
    public static final int REQUEST_ID_LENGTH = 64;

    /** 设备端 APP / H5 / WECHAT / PC */
    public static final int DEVICE_TYPE_LENGTH = 16;

    private InputLimits() {
    }
}
