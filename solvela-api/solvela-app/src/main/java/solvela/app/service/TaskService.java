package solvela.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.app.domain.TaskStageItem;
import solvela.app.domain.TaskView;
import solvela.enums.TaskRecordStatusEnum;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.TaskCenterItem;
import solvela.marketing.api.TaskStageView;

import java.math.BigDecimal;
import java.util.List;

/**
 * 任务中心的接入层：<b>翻译 + 组装</b>，没有业务逻辑。
 *
 * <p>做没做完、发没发奖，全部由任务域说了算 —— 本类一行都不判。
 * 它只做网关该做的两件事：把内部状态翻成<b>给用户看的话</b>，把域的 view 组装成 C 端的形状。
 * 与活动、资产两条链路同一套分工。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final ActivityApi activityApi;

    /**
     * 我的全部任务。没有任务型活动时返回空列表 —— 那不是错误。
     *
     * <p>聚合发生在营销服务<b>进程内</b>（{@code getMyTasks}），
     * 网关不去循环调「单活动任务」那个接口 —— 那是跨进程的 N+1。
     *
     * @param memberId 会员号，<b>由控制器从登录态取</b>
     */
    public List<TaskView> listMyTasks(Long memberId) {
        return activityApi.getMyTasks(memberId).stream()
                .map(TaskService::toView)
                .toList();
    }

    private static TaskView toView(TaskCenterItem item) {
        return new TaskView(
                item.taskId(),
                item.taskName(),
                item.taskGroup(),
                plain(item.target()),
                plain(item.current()),
                statusText(item.status()),
                item.status() == TaskRecordStatusEnum.DISPATCHED,
                summary(item),
                item.stages().stream().map(TaskService::toStage).toList(),
                item.actionUrl());
    }

    private static TaskStageItem toStage(TaskStageView stage) {
        return new TaskStageItem(plain(stage.target()), stage.rewardText(), stage.reached());
    }

    /**
     * 一行摘要，给<b>只有一个档位</b>的任务用（绝大多数任务是这种）。
     *
     * <p>🔴 多档位<b>不</b>在这里拼成「A / B」。那正是这次要修掉的展示：
     * 「签到 1 天得 188 积分、连签 5 天再得 8 元」压成一个斜杠串之后，
     * 用户看不出哪个奖对应哪一档，更看不出自己已经拿到了第一档。
     * 多档位由前端按 stages 画阶梯，这里给 null，前端就不画那行摘要。
     */
    private static String summary(TaskCenterItem item) {
        return item.stages().size() == 1 ? item.stages().getFirst().rewardText() : null;
    }

    /**
     * 状态 → 给用户看的一句话。
     *
     * <h3>用 switch 表达式，不给兜底分支</h3>
     * 任务域新增一个状态时<b>这里编译不过</b>，而不是悄悄落进 default 显示成「进行中」。
     * 与抽奖那边翻译 reject reason 是同一个理由：两边分开发版，
     * 编译期能拦住的东西不该留到运行期。
     *
     * <p>{@code null} 表示还没有进度记录 —— 用户从没触发过这个事件，
     * 和「进行中 0 次」是两件事，说法也该不一样。
     */
    private static String statusText(TaskRecordStatusEnum status) {
        if (status == null) {
            return "未开始";
        }
        return switch (status) {
            case RUNNING -> "进行中";
            // COMPLETED 是「达标了、发奖还在路上」的瞬时态。对用户说「已完成」即可，
            // 不必解释发奖是异步的 —— 他下一次刷新就会看到「已发奖」
            case COMPLETED -> "已完成";
            case DISPATCHED -> "已发奖";
            case EXPIRED -> "已过期";
        };
    }

    /** 数值一律按十进制字符串下发，前端走 Decimal —— 金额型任务的进度是小数 */
    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
