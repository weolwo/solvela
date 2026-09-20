package solvela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.member.api.GradeGrowthLogView;
import solvela.member.api.MemberGradeApi;
import solvela.member.api.MemberGradeView;

import java.util.List;

/**
 * 我的会员等级。
 *
 * <h3>🔴 全是只读，一个写接口都没有</h3>
 * 等级是<b>派生状态</b> —— 跟着成长值走，而成长值只从业务动作来
 * （见 {@code BizActionEvent} 那条打点管道）。给 C 端开一个写入口，
 * 等于给刷等级开了一扇门。人工调级只在管理端，且原因必填、操作人留痕。
 *
 * <h3>🔴 memberId 一律从登录态取，绝不接受客户端传</h3>
 * 接受客户端传就等于「查任意人的等级和成长值明细」。
 *
 * @author alaric
 * @date 2026-09-20
 */
@Tag(name = "我的等级")
@RestController
@RequestMapping("/grade")
@RequiredArgsConstructor
public class GradeController {

    /** 成长值明细默认给多少条。服务端还有一道 50 的硬上限 */
    private static final int DEFAULT_LOG_LIMIT = 20;

    private final MemberGradeApi memberGradeApi;

    /**
     * 我的等级页。
     *
     * <p>⚠️ 没参与过的会员<b>也有返回</b>（0 级 + 完整阶梯），不是 404 ——
     * 「你还没开始」和「查不到」对用户是两件事，而这个页面正是要对新人说前一句。
     */
    @Operation(summary = "我的等级：当前档位、成长值、距下一档、保级状态与完整阶梯")
    @GetMapping
    public MemberGradeView myGrade() {
        return memberGradeApi.myGrade(CurrentMember.require().memberId());
    }

    /**
     * 我的成长值明细。
     *
     * <p>倍率与基数一起给 —— 缓冲期看到「+200」而自己只做了一件值 100 的事时，
     * 用户第一反应是系统算错了。把「100 × 2」摆出来，那条加速规则才真的被感知到。
     */
    @Operation(summary = "我的成长值明细，新的在前")
    @GetMapping("/growthLog")
    public List<GradeGrowthLogView> myGrowthLog(
            @RequestParam(required = false, defaultValue = "" + DEFAULT_LOG_LIMIT) int limit) {
        return memberGradeApi.myGrowthLog(CurrentMember.require().memberId(), limit);
    }
}
