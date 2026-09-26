package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import solvela.member.api.MemberRegisterType;

/**
 * 手机号 + 密码注册的入参。
 *
 * <h3>只有三个字段，账号和昵称不在这里</h3>
 * {@code member_name} 与 {@code nickname} 由会员域自动生成，用户注册后自己改 ——
 * 与微信同一个做法，DDL 注释里那句「用户可改」说的就是这件事。
 * 让用户在注册时起账号，要多一轮「已被占用」的往返，而此刻他要的只是进去。
 *
 * <h3>刻意没有 confirmPassword</h3>
 * 「两次密码是否一致」是<b>纯交互问题</b>：用户在同一个表单里打了两遍。
 * 传到服务端再比一次，不会发现任何前端发现不了的问题，只是多一个字段、
 * 多一条服务端错误消息、多一处两边可能不一致的措辞。前端自己拦掉。
 *
 * <h3>刻意没有 registerSource</h3>
 * 让客户端自己声明「我从哪来」等于让它随便填 —— 而 {@code t_member.register_source}
 * 是运营用来分析渠道、风控用来识别批量注册的列，可被任意伪造就没有价值。
 * 由网关按 {@link #deviceType} 推导，见 {@code MemberLoginService.register}。
 *
 * <h3>⚠️ 2026-09-09 破坏性变更：phone 改成 identity，并加了 registerType</h3>
 * 理由同 {@code MemberLoginRequest} —— 「继续叫 phone 但有时候放的是邮箱」
 * 是一个迟早会骗到人的字段名。
 *
 * @param registerType 注册方式。不传按 {@link MemberRegisterType#PHONE_PASSWORD} 兜底
 * @param identity     手机号或邮箱。<b>刻意不加 {@code @Pattern}</b> —— 格式校验在
 *                     {@code MemberPhoneUtil} / {@code MemberEmailUtil} 的 normalize 里，
 *                     与登录同一份规则
 * @param emailCode    邮箱验证码，仅 {@link MemberRegisterType#EMAIL_CODE} 时必填
 * @param smsCode      短信验证码，仅 {@link MemberRegisterType#PHONE_PASSWORD} 时必填。
 *                     是否真的必填由服务端的
 *                     {@code solvela.member.register.phone-code-required} 决定 ——
 *                     客户端<b>永远传</b>就行，服务端不要它时多传一个字段没有代价
 * @param password     密码明文，依赖 HTTPS 传输。<b>强度规则不在这里</b>，
 *                     在会员域的 {@code MemberPasswordPolicy}，只有那一处。
 *                     <b>邮箱注册时可以不填</b> —— 那种会员之后走验证码登录
 * @param deviceType   设备端：APP / H5 / WECHAT / PC，不传按 H5 记
 */
@Schema(description = "注册入参")
public record MemberRegisterRequest(

        @Schema(description = "注册方式：PHONE_PASSWORD / EMAIL_CODE")
        MemberRegisterType registerType,

        @Schema(description = "手机号或邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请输入手机号或邮箱") String identity,

        @Schema(description = "邮箱验证码，邮箱注册时必填")
        String emailCode,

        @Schema(description = "短信验证码，手机号注册时必填")
        String smsCode,

        @Schema(description = "密码。手机号注册必填；邮箱注册可不填，之后走验证码登录")
        String password,

        @Schema(description = "设备端：APP/H5/WECHAT/PC")
        String deviceType,
        @Schema(description = "Web 端传 true：凭证只经 HttpOnly cookie 下发，响应体里不带。"
                + "App / 自动化测试不传，凭证照旧在响应体里")
        Boolean useCookie) {

    /** 不传时按最早的那条通道兜底 —— 域里也有同样的兜底，两处一致。 */
    public boolean cookieDelivery() {
        return Boolean.TRUE.equals(useCookie);
    }

    public MemberRegisterType typeOrDefault() {
        return registerType == null ? MemberRegisterType.PHONE_PASSWORD : registerType;
    }
}
