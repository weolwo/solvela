package solvela.marketing.api;

import solvela.enums.TaskRecordStatusEnum;

import java.math.BigDecimal;
import java.util.List;
import java.util.List;

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
 * @param target       进度条的满格值 = <b>最高档的阈值</b>（没有档位时退回 rule_config 的目标）。
 *                     次数型是次数、金额型是金额，所以是 BigDecimal 不是 int。
 *                     取最高档而不是 rule_config.targetCount：那两个是不同的源，
 *                     而<b>发奖判的是档位</b>，进度条必须跟着发奖走
 * @param current      当前进度。<b>没有记录时为 0</b>（还没开始做），不是 null
 * @param status       进度状态。<b>没有记录时为 null</b> —— 「还没开始」和「进行中 0 次」
 *                     是两件事，前者用户从没触发过这个事件
 * @param stages       档位列表，按 level 升序。<b>阶梯任务的价值全在这里</b>：
 *                     「签到 1 天得 188 积分、连签 5 天再得 8 元」是两件事。
 *                     旧版把它压成一个 rewardText（各档奖励名用「/」拼起来），
 *                     用户看不出哪个奖对应哪一档，也看不出自己已经拿到了第一档。
 *                     <p>无档位任务也有一条，前端渲染成和以前一样的单行奖励
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
        List<TaskStageView> stages,
        String actionUrl,
        Integer sortWeight) {
}
