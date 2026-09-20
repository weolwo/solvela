package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.MemberSignApi;
import solvela.member.api.MemberSignResult;
import solvela.member.sign.MemberSignService;

/**
 * {@link MemberSignApi} 的 HTTP 薄壳。
 *
 * <p>与其余 {@code *InternalController} 同一个形状：{@code implements} 契约接口而不是
 * 自己写 {@code @PostMapping} —— Spring MVC 认得接口上的 {@code @HttpExchange}，
 * 所以<b>路径与方法只在契约里定义一次</b>。自己写一遍映射的话，
 * 网关侧的客户端代理和这里的服务端映射就是两份，改一处忘另一处的表现是 404，
 * 而且要等到联调才发现。
 *
 * <p>方法体是一行转发，<b>这里不许出现任何业务判断</b>。
 *
 * @author alaric
 * @date 2026-09-17
 */
@RestController
@RequiredArgsConstructor
public class MemberSignInternalController implements MemberSignApi {

    private final MemberSignService memberSignService;

    @Override
    public MemberSignResult sign(Long memberId) {
        return memberSignService.sign(memberId);
    }

    @Override
    public boolean signedToday(Long memberId) {
        return memberSignService.signedToday(memberId);
    }
}
