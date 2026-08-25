package solvela.app.module.login.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.common.annoation.NoNeedLogin;
import solvela.base.common.constant.RequestHeaderConst;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaRequestUtil;
import solvela.base.common.util.SolvelaServletUtil;
import solvela.app.config.StpMemberUtil;
import solvela.app.module.login.domain.RequestMember;
import solvela.app.module.login.domain.form.MemberLoginForm;
import solvela.app.module.login.domain.vo.MemberLoginResultVO;
import solvela.app.module.login.service.MemberLoginService;

/**
 * 会员登录。
 *
 * <p>路径统一挂在 {@code /app} 下：本服务将来会和管理端一起放在同一个域名的不同 location 后面，
 * 前缀能让网关的转发规则只写一条。
 *
 * @Date 2026-08-25
 */
@RestController
@RequestMapping("/app/login")
@RequiredArgsConstructor
@Tag(name = "会员登录")
public class MemberLoginController {

    private final MemberLoginService memberLoginService;

    @NoNeedLogin
    @PostMapping
    @Operation(summary = "手机号密码登录")
    public ResponseDTO<MemberLoginResultVO> login(@Valid @RequestBody MemberLoginForm loginForm, HttpServletRequest request) {
        // ip / userAgent 在 controller 层取，service 不碰 HttpServletRequest ——
        // 这样 service 可以被定时任务、MQ 消费者之类没有请求上下文的地方调用
        String ip = SolvelaServletUtil.getClientIP(request);
        String userAgent = request.getHeader(RequestHeaderConst.USER_AGENT);
        return memberLoginService.login(loginForm, ip, userAgent);
    }

    /**
     * 取当前登录态。前端刷新页面时用它恢复用户信息，返回结构与登录接口一致。
     *
     * <p>⚠️ 刻意<b>不</b>加 {@code @NoNeedLogin}：没登录就该收到「登录失效」，
     * 让前端走统一的跳登录逻辑，而不是收到一个 data 为 null 的成功响应再自己判断。
     */
    @GetMapping("/getLoginInfo")
    @Operation(summary = "获取当前登录会员信息")
    public ResponseDTO<MemberLoginResultVO> getLoginInfo() {
        RequestMember requestMember = (RequestMember) SolvelaRequestUtil.getRequestUser();

        MemberLoginResultVO result = new MemberLoginResultVO();
        result.setMemberId(requestMember.getMemberId());
        result.setMemberName(requestMember.getMemberName());
        result.setNickname(requestMember.getNickname());
        result.setAvatarFileId(requestMember.getAvatarFileId());
        result.setGender(requestMember.getGender());
        result.setToken(StpMemberUtil.getTokenValue());
        return ResponseDTO.ok(result);
    }

    @GetMapping("/logout")
    @Operation(summary = "退出登录")
    public ResponseDTO<String> logout() {
        return memberLoginService.logout((RequestMember) SolvelaRequestUtil.getRequestUser());
    }
}
