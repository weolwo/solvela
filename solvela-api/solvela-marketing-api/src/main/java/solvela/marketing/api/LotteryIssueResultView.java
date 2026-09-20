package solvela.marketing.api;

import java.time.LocalDateTime;

/**
 * 一期的开奖结果。往期列表用。
 *
 * @param issueNo       期号
 * @param winningNumber 开奖号码
 * @param settleTime    <b>实际</b>开奖时刻，不是计划开奖时刻 ——
 *                      往期看的是「什么时候真的开的」
 * @author alaric
 * @date 2026-09-18
 */
public record LotteryIssueResultView(String issueNo, String winningNumber, LocalDateTime settleTime) {
}
