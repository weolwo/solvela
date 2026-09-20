package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.TaskView;
import solvela.app.service.TaskService;
import solvela.member.api.MemberSignApi;
import solvela.member.api.MemberSignResult;

import java.util.List;

/**
 * 任务中心。
 *
 * <h3>没有 @Anonymous：任务中心是「我的进度」</h3>
 * 和活动列表不同 —— 那个匿名可看是因为它是所有人的入口；
 * 而任务中心的每一条都带着<b>这个会员做到哪了</b>，没有登录态就没有内容。
 *
 * <h3>🔴 没有 claim 接口，这是后端设计</h3>
 * 任务达标自动发奖，任务状态里没有 CLAIMED。别加一个「领取」端点 ——
 * 那需要先在任务运行态加一个状态，是产品决策，不是补个接口的事。
 */
@Tag(name = "任务中心")
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 签到。
     *
     * <p>🔴 它在<b>会员</b>契约里，不在任务契约里 —— 签到是会员自己的行为，
     * 不是任务的一部分（见 {@link MemberSignApi} 的类注释）。
     * 放在本控制器上只是因为<b>用户是在任务中心点它的</b>，那是 UI 的事实，不是领域的。
     */
    private final MemberSignApi memberSignApi;

    /**
     * 我的全部任务：当前可见的任务型活动下的任务，合成一份。
     *
     * <p>没有任务型活动、或都没配任务时返回<b>空数组</b>，不是 404。
     *
     * <p>刻意<b>不按活动分开</b>：C 端任务中心是一个独立 tab，
     * 用户不关心「这个任务属于哪场活动」。真需要活动内的任务页时，
     * 契约里那个 {@code getTaskCenter(activityCode, memberId)} 已经在了。
     */
    @GetMapping
    public List<TaskView> listMyTasks() {
        return taskService.listMyTasks(CurrentMember.require().memberId());
    }

    /**
     * 签到。一天只算一次，重复点<b>不是错误</b>。
     *
     * <h3>🔴 memberId 从登录态取，绝不接受客户端传</h3>
     * 让客户端传的话，任何人都能替别人签到 —— 而签到会推任务进度、
     * 进度达标会自动发奖，那条路的终点是真的资产。
     * 与本仓其他 C 端写接口同一条规矩。
     *
     * <h3>返回"今天是不是第一次"，而不是"涨了多少进度"</h3>
     * 进度是异步推的（{@code task-event-executor}），签到返回的那一刻它可能还没算完。
     * 硬要同步返回就得让签到接口去等任务引擎 —— 那等于把那道异步隔离拆了，
     * 任务系统抖一下签到按钮就转圈。端上要看进度，刷新一次任务列表即可。
     */
    @PostMapping("/sign")
    public MemberSignResult sign() {
        return memberSignApi.sign(CurrentMember.require().memberId());
    }

    /**
     * 今天签过没有。给按钮状态用 —— 页面进来时问一次，决定按钮是「签到」还是「已签到」。
     */
    @GetMapping("/sign/today")
    public boolean signedToday() {
        return memberSignApi.signedToday(CurrentMember.require().memberId());
    }
}
