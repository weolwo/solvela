package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 绑定 / 更换邮箱的入参。
 *
 * <p>🔴 <b>没有 memberId</b>。会员号由网关从令牌解析后填入 ——
 * 收客户端传的 memberId 等于「说自己是谁就是谁」。
 *
 * @param email        要绑定的新邮箱
 * @param code         发到<b>新邮箱</b>的验证码，必填 —— 它证明这个邮箱归他
 * @param currentPassword 当前密码。<b>换绑时</b>与 {@link #oldEmailCode} 二选一
 * @param oldEmailCode 发到<b>旧邮箱</b>的验证码。给没设过密码的会员用
 */
@Schema(description = "绑定/更换邮箱")
public record EmailBindRequest(

        @Schema(description = "新邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请输入邮箱")
        @Size(max = InputLimits.IDENTITY_LENGTH, message = "邮箱过长") String email,

        @Schema(description = "新邮箱收到的验证码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请输入验证码")
        @Size(max = InputLimits.CODE_LENGTH, message = "验证码不对") String code,

        @Schema(description = "当前密码。更换邮箱时与旧邮箱验证码二选一")
        @Size(max = InputLimits.PASSWORD_LENGTH, message = "密码过长")
        String currentPassword,

        @Schema(description = "旧邮箱收到的验证码。更换邮箱时与当前密码二选一")
        @Size(max = InputLimits.CODE_LENGTH, message = "验证码不对")
        String oldEmailCode) {
}
