package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.Anonymous;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.ActivityView;
import solvela.app.domain.DrawRequest;
import solvela.app.domain.DrawView;
import solvela.app.service.ActivityService;

/**
 * 活动。
 *
 * <h3>返回的是数据本身，不是信封</h3>
 * 成功 = 2xx + 数据；失败 = 4xx/5xx + {@code ApiErrorResponse}。
 * 与登录那条链路同一套契约。
 *
 * <h3>路由与下游的路由是两回事</h3>
 * 本控制器挂在 {@code /activity/**}（公网），营销服务的端点挂在 {@code /internal/**}。
 * 🔴 入口层必须把 {@code /internal/**} 整体挡在外面 —— 那些端点收明文密码、
 * 直接指定奖池、直接生成资产提案，一个都不该对公网开放。
 */
@Tag(name = "活动")
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 活动详情：基础信息、时间、展示配置、规则正文，以及此刻能不能参与/领奖。
     *
     * <p>{@code @Anonymous}：<b>没登录也能看</b>。活动页是分享出去的入口，
     * 要求先登录才能看一眼，等于把分享链路掐断 —— 抽奖那一步再要求登录不迟。
     */
    @Anonymous
    @GetMapping("/{activityCode}")
    public ActivityView getActivity(@PathVariable String activityCode) {
        return activityService.getActivity(activityCode);
    }

    /**
     * 抽一次。
     *
     * <p>会员号从登录态取，<b>客户端传的一律不认</b>——否则就是「替别人抽奖」。
     * 奖池由活动的编排脚本算，客户端不传也传不了。
     *
     * <p>幂等键由客户端生成（一次点击一个）。它不是可选的：
     * 网络超时不代表没抽，没有它就只能在「可能重复发奖」和「可能白扣一次机会」之间选一个。
     */
    @PostMapping("/{activityCode}/draw")
    public DrawView draw(@PathVariable String activityCode, @RequestBody @Valid DrawRequest request) {
        return activityService.draw(activityCode, CurrentMember.require().memberId(), request);
    }
}
