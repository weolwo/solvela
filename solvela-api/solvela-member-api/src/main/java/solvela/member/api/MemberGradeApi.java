package solvela.member.api;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * 会员等级：<b>会员自己那一侧</b>。
 *
 * <h3>只读</h3>
 * 等级<b>没有任何 C 端写入口</b>，一个都不该有。它是派生状态 ——
 * 跟着成长值走，而成长值只从业务动作来（见 {@code BizActionEvent} 那条管道）。
 * 给 C 端开一个写接口，等于给刷等级开了一扇门。
 *
 * @author alaric
 * @date 2026-09-20
 */
@HttpExchange("/internal/grade")
public interface MemberGradeApi {

    /** 我的等级页。阶梯与权益一并返回，见 {@link MemberGradeView} */
    @GetExchange("/me")
    MemberGradeView myGrade(@RequestParam("memberId") Long memberId);

    /**
     * 我的成长值明细，新的在前。
     *
     * <p>⚠️ 上限由服务端兜底（{@code limit} 超过上限按上限算）——
     * C 端传什么数都不该把库拖垮。
     */
    @GetExchange("/growthLog")
    List<GradeGrowthLogView> myGrowthLog(@RequestParam("memberId") Long memberId,
                                         @RequestParam("limit") int limit);
}
