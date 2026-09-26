package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import solvela.member.api.MemberLoginType;

/**
 * 登录入参。三种方式共用一个请求体。
 *
 * <p>用 record：请求体读进来之后<b>不该被改</b>。上一版是 {@code @Data} 的可变类，
 * 于是「这个值到了第三步还是不是用户传的那个」要读全链路才能回答。
 *
 * <h3>⚠️ 2026-09-09 破坏性变更：phone/password 改成 identity/credential</h3>
 * 加邮箱通道时最省事的做法是「继续叫 phone，但有时候放的是邮箱」——
 * 那种字段名迟早会骗到某个人，而它骗人的方式是让他写出一段
 * 「按手机号规范化一个邮箱」的代码，且不报错。
 *
 * <p>保留 phone 作别名也是一条路，但那正是本仓到处在警告的「同一个东西两个来源」。
 * 客户端要跟着改一版。
 *
 * @param loginType  登录方式。不传按 {@link MemberLoginType#PHONE_PASSWORD} 兜底
 * @param identity   手机号或邮箱。<b>刻意不加 {@code @Pattern}</b> —— 格式校验在
 *                   {@code MemberPhoneUtil} / {@code MemberEmailUtil} 的 normalize 里，
 *                   规范化和校验本来就是同一件事的两面。在这里再写一条正则，
 *                   就是第二份格式规则，两份迟早对不上
 * @param credential 密码明文或邮箱验证码，依赖 HTTPS 传输
 * @param deviceType 设备端：APP / H5 / WECHAT / PC，不传按 H5 记。
 *                   取值对齐 {@code t_member_login_log.device_type}
 */
@Schema(description = "登录入参")
public record MemberLoginRequest(

        @Schema(description = "登录方式：PHONE_PASSWORD / EMAIL_PASSWORD / EMAIL_CODE")
        MemberLoginType loginType,

        @Schema(description = "手机号或邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请输入手机号或邮箱") String identity,

        @Schema(description = "密码或邮箱验证码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请输入密码或验证码") String credential,

        @Schema(description = "二次验证码。仅当服务端回 DEVICE_VERIFICATION_REQUIRED 时才需要带")
        String verificationCode,

        @Schema(description = "设备端：APP/H5/WECHAT/PC")
        String deviceType,
        @Schema(description = "Web 端传 true：凭证只经 HttpOnly cookie 下发，响应体里不带。"
                + "App / 自动化测试不传，凭证照旧在响应体里")
        Boolean useCookie,
        @Schema(description = "记住我。仅 useCookie 时有意义：true 下发持久 cookie，false 下发会话 cookie（关浏览器即失效）")
        Boolean remember) {

    /** 不传时按最早的那条通道兜底 —— 域里也有同样的兜底，两处一致。 */
    public boolean cookieDelivery() {
        return Boolean.TRUE.equals(useCookie);
    }

    public boolean rememberMe() {
        return Boolean.TRUE.equals(remember);
    }

    public MemberLoginType typeOrDefault() {
        return loginType == null ? MemberLoginType.PHONE_PASSWORD : loginType;
    }
}
