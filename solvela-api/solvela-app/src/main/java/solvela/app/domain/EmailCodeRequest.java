package solvela.app.domain;

import solvela.app.ratelimit.RateLimitAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import solvela.member.api.EmailCodeScene;

/**
 * 索取邮箱验证码的入参。
 *
 * <p>🔴 {@code scene} <b>必填且不给默认值</b>。给个默认值的话，客户端漏传时会拿到
 * 一个「能用但不是他要的」验证码 —— 比如按注册场景发的码，用户拿去重置密码时验不过，
 * 而两边看起来都很正常。宁可 400。
 *
 * @param scene 用途：REGISTER / LOGIN / BIND / RESET_PASSWORD
 * @param email 收件邮箱。格式校验在会员域的 {@code MemberEmailUtil.normalize}，这里不重复写
 */
@Schema(description = "索取邮箱验证码")
public record EmailCodeRequest(

        @Schema(description = "用途：REGISTER / LOGIN / BIND / RESET_PASSWORD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请指定验证码用途") EmailCodeScene scene,

        @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请输入邮箱") String email) implements RateLimitAccount {

    /** 网关限流的账号维度数它（规范化后算 HMAC，不落明文），格式校验仍在会员域 */
    @Override
    public String rateLimitAccount() {
        return email;
    }

}
