package solvela.marketing.api;

import solvela.enums.TaskRecordStatusEnum;

import java.math.BigDecimal;

/**
 * 任务中心里的一条任务。
 *
 * <h3>🔴 没有「领取」这一步</h3>
 * {@code TaskRecordStatusEnum} 是 RUNNING / COMPLETED / <b>DISPATCHED</b> / EXPIRED ——
 * 没有 CLAIMED。任务达标时由 {@code TaskPrizeDispatcher.dispatchReachedStages}
 * <b>自动发奖</b>，用户不需要（也不能）点一下领。
 *
 * <p>所以这个 record 里没有 claimable 之类的字段，C 端也不该画「领取」按钮 ——
 * 画了就是一个点了什么都不会发生的按钮，而奖励其实早就到账了，
 * 用户会以为自己没领到。
 *
 * <p>要改成「达标后手动领」是<b>产品决策</b>，那要在任务运行态加一个状态与一个接口，
 * 不是前端加个按钮的事。
 *
 * @param taskId       任务配置 id。任务没有对外编码，C 端就用它寻址
 * @param taskName     任务名称
 * @param taskGroup    任务分组，运营配的。C 端可以据此分区展示，为空表示不分组
 * @param target       目标值。次数型是次数，金额型是金额 —— 所以是 BigDecimal 不是 int
 * @param current      当前进度。<b>没有记录时为 0</b>（还没开始做），不是 null
 * @param status       进度状态。<b>没有记录时为 null</b> —— 「还没开始」和「进行中 0 次」
 *                     是两件事，前者用户从没触发过这个事件
 * @param rewardText   奖励文案，如「+10 积分」。<b>由后端拼</b> ——
 *                     前端拼这句话等于把奖励规则复制一份，规则一改就是两处不一致
 * @param actionUrl    「去完成」跳哪，运营配的。为空表示这个任务没有跳转入口
 *                     （比如「每日登录」，用户已经在里面了）
 * @param sortWeight   排序权重，由运营配。<b>调用方按它排，不要自己发明顺序</b>
 */
public record TaskCenterItem(
        Long taskId,
        String taskName,
        String taskGroup,
        BigDecimal target,
        BigDecimal current,
        TaskRecordStatusEnum status,
        String rewardText,
        String actionUrl,
        Integer sortWeight) {
}
