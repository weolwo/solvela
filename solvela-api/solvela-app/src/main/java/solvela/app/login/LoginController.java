package solvela.app.login;

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
public class LoginController {

    private final LoginService loginService;

    /**
     * 手机号 + 密码登录。
     *
     * <p>IP 在<b>端上</b>取，不传进 service —— service 收 {@code HttpServletRequest}
     * 就意味着它只能被 HTTP 调用，短信验证码登录、第三方登录、内部工具都没法复用同一段逻辑。
     */
    @Anonymous
    @PostMapping("/login")
    public LoginResult login(@RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest) {
        return loginService.login(request, ClientIp.of(servletRequest));
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
        loginService.logout(currentToken(servletRequest), member.memberId(), ClientIp.of(servletRequest));
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
