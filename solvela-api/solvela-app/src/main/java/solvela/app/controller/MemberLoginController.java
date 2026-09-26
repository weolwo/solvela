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
import solvela.app.domain.EmailBindRequest;
import solvela.app.domain.EmailCodeRequest;
import solvela.app.domain.PhoneBindRequest;
import solvela.app.domain.SessionRevokeRequest;
import solvela.member.api.MemberContactView;
import solvela.app.domain.SmsCodeRequest;
import solvela.auth.member.MemberSession;

import java.util.List;
import solvela.app.domain.MemberLoginRequest;
import solvela.app.domain.MemberRegisterRequest;
import solvela.app.domain.MemberResult;
import solvela.app.domain.PasswordResetRequest;
import solvela.app.domain.PasswordResetView;
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
     * 注册。两种方式共用这一条路由，由请求体里的 registerType 决定。
     * 成功后<b>直接返回令牌</b>，形状与登录完全一致 ——
     * 客户端不用为注册单独写一套「存令牌 + 存会员信息」的代码。
     *
     * <h3>⚠️ 两条通道的可信度差得很远</h3>
     * <ul>
     *   <li><b>EMAIL_CODE</b>：验码即证明这个邮箱归他，<b>拿别人的邮箱注册不了</b>；</li>
     *   <li><b>PHONE_PASSWORD</b>：全仓仍然<b>没有短信基础设施</b>，所以这条路
     *       至今是<b>任何人都能拿别人的手机号建号</b>，唯一的缓解是会员域的 IP 限频
     *       与设备限频。而 {@code uk_mbr_phone_hash} 是唯一约束 ——
     *       号被占了，真机主就注册不了了。</li>
     * </ul>
     * 🔴 上线前手机号那条仍然必须补短信验证码。邮箱这条<b>不能替代它</b>，
     * 它只是提供了另一条已验证的入口。
     */
    @Anonymous
    @PostMapping("/register")
    public MemberResult register(@RequestBody @Valid MemberRegisterRequest request,
                                 HttpServletRequest servletRequest) {
        return memberLoginService.register(request, ClientIp.of(servletRequest));
    }

    /**
     * 索取邮箱验证码。四个场景共用这一条路由，用途由请求体里的 scene 指定。
     *
     * <p>标 {@link Anonymous}：注册、登录、重置密码这三个场景本来就没有登录态。
     * 绑定邮箱（BIND）有登录态，但它照样走这条路由 —— {@code @Anonymous}
     * 的类注释说得很清楚：标了它<b>不代表拿不到身份</b>，带了有效令牌的请求照样会被识别。
     *
     * <p>🔴 返回 204，<b>不返回任何关于这个邮箱的信息</b>。
     * 「已发送」「该邮箱未注册」这类区分会把它变成账号枚举接口。
     */
    @Anonymous
    @PostMapping("/email/code")
    public ResponseEntity<Void> sendEmailCode(@RequestBody @Valid EmailCodeRequest request,
                                              HttpServletRequest servletRequest) {
        memberLoginService.sendEmailCode(request, ClientIp.of(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * 索取短信验证码。
     *
     * <p>与邮箱那条分成两条路由，而不是一条带 channel 参数：两条通道的场景集合不一样
     * （短信没有 BIND）、限频不一样、失败措辞不一样。合成一条会得到一个
     * 「大部分字段在大部分情况下没用」的入参。
     *
     * <p>⚠️ 短信是<b>要花钱</b>的接口。它匿名、无成本地暴露在公网上，
     * 唯一的保护是域里的 IP 与目标日限（比邮箱紧得多）。改动那两个配置前先想清楚。
     */
    @Anonymous
    @PostMapping("/sms/code")
    public ResponseEntity<Void> sendSmsCode(@RequestBody @Valid SmsCodeRequest request,
                                            HttpServletRequest servletRequest) {
        memberLoginService.sendSmsCode(request, ClientIp.of(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * 登录。三种方式共用这一条路由，由请求体里的 loginType 决定。
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
     * 绑定 / 更换邮箱。<b>要登录</b>（所以没有 {@link Anonymous}）。
     *
     * <p>绑完之后这个邮箱就能用来登录、能用来重置密码 —— 它<b>新增了一条登录身份</b>，
     * 不是改个昵称。所以换绑时域里还会要求「当前密码」或「旧邮箱验证码」，
     * 拦的是「会话被盗 → 换绑 → 重置密码 → 永久接管」那条链。
     *
     * <p>返回 204：没有要给客户端的数据。
     */
    @PostMapping("/email/bind")
    public ResponseEntity<Void> bindEmail(@RequestBody @Valid EmailBindRequest request,
                                          HttpServletRequest servletRequest) {
        memberLoginService.bindEmail(request, ClientIp.of(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * 我的联系方式（手机号 / 邮箱，<b>都是脱敏的</b>）。<b>要登录</b>。
     *
     * <p>单独一条路由而不是塞进 {@code /auth/me}：那个结果会进网关缓存和日志，
     * 而手机号邮箱是 PII。代价是多一次往返，换的是明文一次都不出域。
     */
    @PostMapping("/contact")
    public MemberContactView contact() {
        return memberLoginService.contact(CurrentMember.require().memberId());
    }

    /**
     * 用邮箱验证码重置密码。<b>匿名</b> —— 用户正是因为进不去才走这条路。
     *
     * <p>成功之后他在<b>所有设备</b>上的会话都会被吊销。返回被吊销的数量，
     * 客户端要展示出来：点「忘记密码」的最常见原因之一就是「我怀疑号被人动过」，
     * 而「已在 3 台设备上退出登录」正是他要的那个答案。
     */
    @Anonymous
    @PostMapping("/password/reset")
    public PasswordResetView resetPassword(@RequestBody @Valid PasswordResetRequest request,
                                           HttpServletRequest servletRequest) {
        return memberLoginService.resetPassword(request, ClientIp.of(servletRequest));
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
     * 我的登录设备。<b>要登录</b>。
     *
     * <h3>它回答的是「现在有谁登着我的号」</h3>
     * 只列<b>活着的</b>会话 —— 历史登录记录是另一件事（后台的 t_member_login_log），
     * 混进来的话用户会看到一堆早已失效的设备，对着点不动的下线按钮发愁。
     *
     * <p>列表里 {@code current=true} 那一条是用户此刻正在用的。必须标出来：
     * 不标的话他很容易把自己这台点下线，然后当场被踢出去。
     */
    @PostMapping("/sessions")
    public List<MemberSession> sessions(HttpServletRequest servletRequest) {
        return memberLoginService.listSessions(
                CurrentMember.require().memberId(), currentToken(servletRequest));
    }

    /**
     * 让某个会话下线。
     *
     * <p>返回 204。成功与「这个 sessionId 已经不在了」<b>都是 204</b> ——
     * 用户要的结果是「那台设备下线」，而它本来就不在线时，这个结果已经成立。
     * 为此回一个 404 只会让客户端多写一段没用的分支。
     */
    @PostMapping("/sessions/revoke")
    public ResponseEntity<Void> revokeSession(@RequestBody @Valid SessionRevokeRequest request,
                                              HttpServletRequest servletRequest) {
        memberLoginService.revokeSession(CurrentMember.require().memberId(), request.sessionId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 下线<b>除当前之外</b>的所有会话。
     *
     * <p>这是「我的号可能被别人登着」时最有用的那个按钮：一次点掉所有其它设备，
     * 而自己不用重新登。做成「全部下线（含自己）」的话，用户会犹豫要不要点 ——
     * 而犹豫的那几分钟里，别人还登着。
     */
    @PostMapping("/sessions/revokeOthers")
    public ResponseEntity<Void> revokeOtherSessions(HttpServletRequest servletRequest) {
        memberLoginService.revokeOtherSessions(
                CurrentMember.require().memberId(), currentToken(servletRequest), ClientIp.of(servletRequest));
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
