package solvela.admin.module.system.login.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import solvela.base.common.swagger.SchemaEnum;
import solvela.base.common.validator.enumeration.CheckEnum;
import solvela.base.constant.LoginDeviceEnum;
import org.hibernate.validator.constraints.Length;

/**
 * 员工登录
 *
 * @Author 1024创新实验室: 开云
 * @Date 2021-12-19 11:49:45
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
/*
 * 2026-08-06：图形验证码功能已整体移除，本类不再继承 CaptchaForm
 * （原先带的 captchaCode / captchaUuid 两个字段随之消失）。
 * 暴力破解的防线现在只剩「连续登录失败 N 次锁定账号」那一条，见 LoginService。
 */
@Data
public class LoginForm {

    @Schema(description = "登录账号")
    @NotBlank(message = "登录账号不能为空")
    @Length(max = 30, message = "登录账号最多30字符")
    private String loginName;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    @SchemaEnum(desc = "登录终端", value = LoginDeviceEnum.class)
    @CheckEnum(value = LoginDeviceEnum.class, required = true, message = "此终端不允许登录")
    private Integer loginDevice;

    @Schema(description = "邮箱验证码")
    private String emailCode;
}
