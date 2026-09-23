package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.EntitlementClaimResult;
import solvela.member.api.MemberEntitlementApi;
import solvela.member.api.MemberEntitlementView;
import solvela.member.entitlement.service.GradeEntitlementClaimService;
import solvela.member.entitlement.service.GradeEntitlementQueryService;

import java.util.List;

/**
 * {@link MemberEntitlementApi} 的 HTTP 薄壳。
 *
 * <p>与其余 {@code *InternalController} 同一个形状：{@code implements} 契约接口，
 * 路径与方法只在契约里定义一次。方法体是一行转发，这里不许出现任何业务判断。
 *
 * @author alaric
 * @date 2026-09-22
 */
@RestController
@RequiredArgsConstructor
public class MemberEntitlementInternalController implements MemberEntitlementApi {

    private final GradeEntitlementQueryService gradeEntitlementQueryService;
    private final GradeEntitlementClaimService gradeEntitlementClaimService;

    @Override
    public List<MemberEntitlementView> mine(Long memberId, int limit) {
        return gradeEntitlementQueryService.mine(memberId, limit);
    }

    @Override
    public EntitlementClaimResult claim(Long memberId, Long grantId) {
        return new EntitlementClaimResult(gradeEntitlementClaimService.claim(memberId, grantId));
    }
}
