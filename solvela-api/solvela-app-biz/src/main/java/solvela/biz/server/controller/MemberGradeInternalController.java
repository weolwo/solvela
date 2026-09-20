package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.GradeGrowthLogView;
import solvela.member.api.MemberGradeApi;
import solvela.member.api.MemberGradeView;
import solvela.member.grade.service.MemberGradeQueryService;

import java.util.List;

/**
 * {@link MemberGradeApi} 的 HTTP 薄壳。
 *
 * <p>与其余 {@code *InternalController} 同一个形状：{@code implements} 契约接口，
 * 路径与方法只在契约里定义一次。方法体是一行转发，这里不许出现任何业务判断。
 *
 * @author alaric
 * @date 2026-09-20
 */
@RestController
@RequiredArgsConstructor
public class MemberGradeInternalController implements MemberGradeApi {

    private final MemberGradeQueryService memberGradeQueryService;

    @Override
    public MemberGradeView myGrade(Long memberId) {
        return memberGradeQueryService.myGrade(memberId);
    }

    @Override
    public List<GradeGrowthLogView> myGrowthLog(Long memberId, int limit) {
        return memberGradeQueryService.myGrowthLog(memberId, limit);
    }
}
