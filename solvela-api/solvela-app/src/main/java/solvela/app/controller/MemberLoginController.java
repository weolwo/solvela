package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.Anonymous;
import solvela.app.auth.CurrentMember;
import solvela.app.auth.MemberPrincipal;
import solvela.app.domain.MemberLoginRequest;
import solvela.app.domain.MemberRegisterRequest;
import solvela.app.domain.MemberResult;
import solvela.app.service.MemberLoginService;
import solvela.app.web.ClientIp;

/**
 * 会员登录。
 *
 * <h3>返回的是数据本身，不是信封</h3>
 * 成功 = 2xx + 数据；失败 = 4xx/5xx + {@code ApiErrorResponse}（由
 * {@code ApiExceptionHandler} 统一产出）。所以这里看不到任何
 * {@code ResponseDTO.ok(...)} —— 那一层在新契约里不存在了。
 */
@Tag(name = "会员登录")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class MemberLoginController {

    private final MemberLoginService memberLoginService;

    /**
     * 手机号 + 密码注册，成功后<b>直接返回令牌</b>，形状与登录完全一致。
     *
     * <p>客户端因此不用为注册单独写一套「存令牌 + 存会员信息」的代码 ——
     * 拿到 {@code MemberResult} 就走登录成功那条路。
     *
     * <h3>⚠️ 目前没有短信验证码</h3>
     * 全仓没有短信基础设施，所以这条路由现在<b>任何人都能拿别人的手机号建号</b>，
     * 唯一的缓解是会员域里的 IP 限频（见 {@code MemberRegisterService} 的类注释）。
     * 上线前必须补验证码 —— 加一个字段、域里加一步校验，本方法不用动。
     */
    @Anonymous
    @PostMapping("/register")
    public MemberResult register(@RequestBody @Valid MemberRegisterRequest request,
                                 HttpServletRequest servletRequest) {
        return memberLoginService.register(request, ClientIp.of(servletRequest));
    }

    /**
     * 手机号 + 密码登录。
     *
     * <p>IP 在<b>端上</b>取，不传进 service —— service 收 {@code HttpServletRequest}
     * 就意味着它只能被 HTTP 调用，短信验证码登录、第三方登录、内部工具都没法复用同一段逻辑。
     */
    @Anonymous
    @PostMapping("/login")
    public MemberResult login(@RequestBody @Valid MemberLoginRequest request, HttpServletRequest servletRequest) {
        return memberLoginService.login(request, ClientIp.of(servletRequest));
    }

    /**
     * 退出登录。
     *
     * <p>返回 204：这个操作没有任何要给客户端的数据，
     * 硬造一个 {@code {"msg":"操作成功"}} 只是让客户端多写一次解析。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        MemberPrincipal member = CurrentMember.require();
        memberLoginService.logout(currentToken(servletRequest), member.memberId(), ClientIp.of(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * 取当前登录会员。客户端冷启动时用它确认本地令牌还有效。
     */
    @PostMapping("/me")
    public MemberPrincipal me() {
        return CurrentMember.require();
    }

    /**
     * 从请求头里取回当前令牌原文。
     *
     * <p>只有退出登录需要它 —— 吊销的对象是「这一个令牌」，而认证过滤器
     * 只把解析结果（会员身份）传下来，不传凭证本身。
     * 凭证不进上下文是刻意的：进了就会被顺手写进日志或返回给前端。
     */
    private static String currentToken(HttpServletRequest request) {
        String raw = request.getHeader("Authorization");
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed.substring(7).trim() : trimmed;
    }
}
