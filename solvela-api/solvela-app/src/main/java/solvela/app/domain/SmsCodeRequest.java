package solvela.app.domain;

import solvela.app.ratelimit.RateLimitAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import solvela.member.api.SmsScene;

/**
 * 索取短信验证码的入参。
 *
 * <p>🔴 {@code scene} <b>必填且不给默认值</b>，理由与 {@link EmailCodeRequest} 一字不差：
 * 给默认值的话，客户端漏传时会拿到一个「能用但不是他要的」验证码，两边看起来都很正常。
 *
 * @param scene 用途：REGISTER / LOGIN / RESET_PASSWORD。<b>比邮箱少一个 BIND</b> ——
 *              绑定手机号还没做
 * @param phone 收件手机号。格式校验在会员域的 {@code MemberPhoneUtil.normalize}，这里不重复写
 */
@Schema(description = "索取短信验证码")
public record SmsCodeRequest(

        @Schema(description = "用途：REGISTER / LOGIN / RESET_PASSWORD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请指定验证码用途") SmsScene scene,

        @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请输入手机号")
        @Size(max = InputLimits.IDENTITY_LENGTH, message = "手机号格式不对") String phone) implements RateLimitAccount {

    /** 网关限流的账号维度数它（规范化后算 HMAC，不落明文），格式校验仍在会员域 */
    @Override
    public String rateLimitAccount() {
        return phone;
    }

}
