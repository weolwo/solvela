package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.TaskView;
import solvela.app.service.TaskService;

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
}
