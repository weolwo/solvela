package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberLogoutCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.auth.MemberAuthService;

/**
 * {@link MemberAuthApi} 的 HTTP 薄壳。
 *
 * <h3>为什么 implements 接口，而不是自己写 @PostMapping</h3>
 * Spring MVC 认得接口上的 {@code @HttpExchange}，所以<b>路径与方法只在契约里定义一次</b>。
 * 自己写一遍映射的话，网关侧的客户端代理和这里的服务端映射就是两份，
 * 改一处忘另一处 —— 表现是 404，而且要等到联调才发现。
 *
 * <p>⚠️ 本进程里有两个 {@code MemberAuthApi} 类型的 bean（本类与 {@link MemberAuthService}），
 * 所以<b>进程内不要按接口类型注入</b>，要注入就注入实现类。按接口注入的是网关，
 * 那边只有 HTTP 代理一个实现，不存在歧义。
 *
 * <h3>🔴 /internal/member/auth/verify 与 /register 收的都是明文密码</h3>
 * 这两条路由<b>永远不能对公网开放</b>，入口层必须把 {@code /internal/**} 整体挡在外面。
 * {@code /register} 尤其：它<b>不需要任何身份</b>就能建一个会员。
 */
@RestController
@RequiredArgsConstructor
public class MemberAuthInternalController implements MemberAuthApi {

    private final MemberAuthService memberAuthService;

    @Override
    public MemberRegisterResult register(MemberRegisterCmd cmd) {
        return memberAuthService.register(cmd);
    }

    @Override
    public MemberAuthResult authenticate(MemberAuthCmd cmd) {
        return memberAuthService.authenticate(cmd);
    }

    @Override
    public MemberIdentity getAuthIdentity(Long memberId) {
        return memberAuthService.getAuthIdentity(memberId);
    }

    @Override
    public void recordLogout(MemberLogoutCmd cmd) {
        memberAuthService.recordLogout(cmd);
    }
}
