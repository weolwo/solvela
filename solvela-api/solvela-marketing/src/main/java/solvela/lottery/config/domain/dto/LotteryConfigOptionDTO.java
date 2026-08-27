package solvela.lottery.config.domain.dto;

/**
 * 彩票玩法下拉项：工作台顶部「当前玩法」切换用，按活动过滤。
 *
 * 带上号码长度与发行量，是因为同一个活动下的多个玩法往往只在发行规格上有差别
 * （「5位号 10万张」与「7位号 1000万张」），只显示名称的话运营分不清点开的是哪个。
 *
 * @param lotteryCode  彩票编码
 * @param lotteryName  彩票名称
 * @param activityCode 归属活动编码
 * @param numberLength 号码长度
 * @param totalCount   单期发售上限
 * @param status       0-未上线, 1-售卖中
 *
 * @Author alaric
 * @Date 2026-07-27
 */
public record LotteryConfigOptionDTO(String lotteryCode,
                                    String lotteryName,
                                    String activityCode,
                                    Integer numberLength,
                                    Integer totalCount,
                                    Integer status) {
}
