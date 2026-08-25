package solvela.app.module.login.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会员登录表单：手机号 + 密码。
 *
 * <p>⚠️ 短信验证码登录（积分商城更常见的方式）<b>尚未实现</b> —— 需要先接短信通道。
 * {@code t_member.password} 允许为空正是为验证码登录留的，所以会存在
 * 「有账号但没密码」的会员，{@code MemberLoginService} 对这种情况有专门分支。
 *
 * @Date 2026-08-25
 */
@Data
@Schema(description = "会员登录表单")
public class MemberLoginForm {

    /**
     * 🔴 这里只校验非空，<b>不校验格式</b>。格式判断在 service 里通过
     * {@code MemberPhoneUtil.normalize} 完成，因为规范化和校验是同一件事的两面：
     * 在表单上加一个 {@code @Pattern} 会变成第二份手机号规则，两份规则迟早对不上
     * （典型症状：表单放行了 "+86138…"，service 却按另一套规则算出不同的摘要）。
     */
    @Schema(description = "手机号")
    @NotBlank(message = "请输入手机号")
    private String phone;

    @Schema(description = "密码（明文，依赖 HTTPS 传输）")
    @NotBlank(message = "请输入密码")
    private String password;

    /**
     * 设备标识，用于登录日志归因与「按设备下线」。
     *
     * <p>由前端声明，取值对齐 {@code t_member_login_log.device_type}：APP / H5 / WECHAT / PC。
     * 不传默认按 H5 记 —— 本服务第一个接入方就是 H5。
     *
     * <p>⚠️ 这是<b>客户端自报</b>的值，可以伪造，只能用于统计口径，
     * 不要拿它做任何安全判断（比如「只有 APP 才能领奖」）。
     */
    @Schema(description = "设备端：APP/H5/WECHAT/PC，默认 H5")
    private String deviceType;
}
