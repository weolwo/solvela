package solvela.marketing.api;

import solvela.enums.PrizeDispatchStatusEnum;

import java.time.LocalDateTime;

/**
 * 我的一条奖励记录。{@code t_prize_log} 的对外投影。
 *
 * <h3>为什么不需要按玩法分开查</h3>
 * 抽奖、任务、彩票的发奖<b>都落在同一张 t_prize_log</b>（表上有 activity_type 列）。
 * 所以「我的奖励记录」是一张表的查询，不是跨三个模块的聚合 ——
 * {@code ActivityApi} 注释里那条「跨玩法必须走 SPI」针对的是
 * 「<b>可领的</b> + 历史记录」里可领的那一半，而 {@code claimPrize} 至今没实现，
 * 今天根本没有「领取」这个概念。真做领取时，那一半才需要 SPI。
 *
 * @param recordId     记录 id
 * @param prizeName    奖品名称，如「10 积分」「中秋月饼礼盒」
 * @param prizeType    奖品类型 SCORE / COUPON / PHYSICAL / BALANCE，对齐 PrizeTypeEnum。
 *                     <b>下发编码</b>，怎么显示由接入层决定
 * @param prizeValue   奖品面值。积分类是数量，实物类可能为空
 * @param activityCode 来自哪个活动。C 端可以据此回跳，也便于客服对单
 * @param activityType 玩法类型 DRAW / TASK / LOTTERY
 * @param status       派发状态 WAITING / SUCCESS / FAIL
 * @param failReason   失败原因，仅 FAIL 时有值。
 *                     ⚠️ 这是<b>内部原因</b>（如「资产账户冻结」），接入层要决定给不给用户看
 * @param createTime   中奖/达标时间
 */
public record PrizeRecordView(
        Long recordId,
        String prizeName,
        String prizeType,
        String prizeValue,
        String activityCode,
        String activityType,
        PrizeDispatchStatusEnum status,
        String failReason,
        LocalDateTime createTime) {
}
