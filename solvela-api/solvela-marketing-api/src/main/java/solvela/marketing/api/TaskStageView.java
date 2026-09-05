package solvela.marketing.api;

import java.math.BigDecimal;

/**
 * 任务的一个档位。<b>阶梯任务的价值全在这里</b>：
 * 「签到 1 天得 188 积分、连签 5 天再得 8 元红包」是两件事，
 * 把它们压成一个目标加一串用斜杠拼起来的奖励名，用户看不出哪个奖对应哪一档。
 *
 * <h3>🔴 target 取自 {@code t_task_prize_mapping.stage_condition}，不是 rule_config</h3>
 * 那一列才是<b>发奖时真正判的阈值</b>（{@code TaskPrizeDispatcher} 读的就是它）。
 * {@code rule_config.targetCount} 是另一个源 —— 两者今天碰巧都等于 5，
 * 但运营把档位改成 1/3/7 而没动 targetCount 时就会分叉，
 * 表现是<b>进度条满了却没发奖</b>，或者反过来发了奖进度条还没满。
 *
 * <p>这和任务中心当初判 {@code status == 2} 是同一类错：
 * <b>展示的判据必须和发放的判据同源</b>。
 *
 * @param level      档位序号，从 1 开始，按它排序
 * @param target     达标阈值
 * @param rewardText 这一档发什么，如「积分188」
 * @param reached    <b>当前周期内</b>是否已达标。未登录时恒 false
 */
public record TaskStageView(
        int level,
        BigDecimal target,
        String rewardText,
        boolean reached) {
}
