package solvela.admin.module.system.login.controller;

import cn.dev33.satoken.stp.StpUtil;
import solvela.web.SolvelaServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import solvela.admin.constant.AdminSwaggerTagConst;
import solvela.admin.module.system.login.domain.LoginForm;
import solvela.admin.module.system.login.domain.LoginResultVO;
import solvela.admin.module.system.login.service.LoginService;
import solvela.admin.util.AdminRequestUtil;
import solvela.base.annotation.AllowAnonymous;
import solvela.base.constant.RequestHeaderConst;
import solvela.base.domain.ResponseDTO;
import solvela.base.web.CurrentUser;
import solvela.admin.module.system.securityprotect.service.Level3ProtectConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 员工登录
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2021-12-15 21:05:46
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_LOGIN)
public class LoginController {
    @Resource
    private LoginService loginService;

    @Resource
    private Level3ProtectConfigService level3ProtectConfigService;

    @AllowAnonymous
    @PostMapping("/login")
    @Operation(summary = "登录 @author 卓大")
    public ResponseDTO<LoginResultVO> login(@Valid @RequestBody LoginForm loginForm, HttpServletRequest request) {
        String ip = SolvelaServletUtil.getClientIP(request);
        String userAgent = request.getHeader(RequestHeaderConst.USER_AGENT);
        return loginService.login(loginForm, ip, userAgent);
    }

    @GetMapping("/login/getLoginInfo")
    @Operation(summary = "获取登录结果信息  @author 卓大")
    public ResponseDTO<LoginResultVO> getLoginInfo() {
        String tokenValue = StpUtil.getTokenValue();
        LoginResultVO loginResult = loginService.getLoginResult(AdminRequestUtil.getRequestUser(), tokenValue);
        loginResult.setToken(tokenValue);
        return ResponseDTO.ok(loginResult);
    }

    @Operation(summary = "退出登录  @author 卓大")
    @GetMapping("/login/logout")
    public ResponseDTO<String> logout() {
        return loginService.logout(CurrentUser.orNull());
    }

    @AllowAnonymous
    @GetMapping("/login/sendEmailCode/{loginName}")
    @Operation(summary = "获取邮箱登录验证码 @author 卓大")
    public ResponseDTO<String> sendEmailCode(@PathVariable String loginName) {
        return loginService.sendEmailCode(loginName);
    }


    @AllowAnonymous
    @GetMapping("/login/getTwoFactorLoginFlag")
    @Operation(summary = "获取双因子登录标识 @author 卓大")
    public ResponseDTO<Boolean> getTwoFactorLoginFlag() {
        // 双因子登录
        boolean twoFactorLoginEnabled = level3ProtectConfigService.isTwoFactorLoginEnabled();
        return ResponseDTO.ok(twoFactorLoginEnabled);
    }
}
