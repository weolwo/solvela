package solvela.app.domain;

/**
 * C 端任务中心里的一条。
 *
 * <h3>🔴 没有「领取」，这是后端设计，不是漏了</h3>
 * 任务达标时自动发奖（{@code TaskPrizeDispatcher}），任务状态里也没有 CLAIMED。
 * 所以<b>前端不该画「领取」按钮</b> —— 画了就是一个点了什么都不会发生的按钮，
 * 而奖励其实早就到账了，用户反而会以为自己没领到。
 *
 * @param taskId      任务 id，前端做 key 用
 * @param taskName    任务名称
 * @param taskGroup   分组，为空表示不分组
 * @param target      目标值，字符串。金额型任务的目标是小数，走 Decimal 展示
 * @param current     当前进度，字符串。同上
 * @param statusText  给用户看的一句话：未开始 / 进行中 / 已完成 / 已发奖 / 已过期。
 *                    <b>由后端给</b> —— 前端做映射表就是第二份状态机，
 *                    域里加一个状态时它会静默变错
 * @param finished    是否已经拿到奖励（DISPATCHED）。只用来选样式，不直接展示
 * @param rewardText  奖励文案，如「+10 积分」。多档任务用「/」连起来
 * @param actionUrl   「去完成」跳哪。为空表示没有跳转入口，前端就不画那个按钮
 */
public record TaskView(
        Long taskId,
        String taskName,
        String taskGroup,
        String target,
        String current,
        String statusText,
        boolean finished,
        String rewardText,
        java.util.List<TaskStageItem> stages,
        String actionUrl) {
}
