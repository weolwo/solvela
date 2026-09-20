package solvela.marketing.api;

import java.time.LocalDateTime;

/**
 * 一个彩票玩法的「当前一期」。
 *
 * <h3>🔴 {@code issueNo} 可能是 {@code null}，那不是错误</h3>
 * 上一期开完奖、下一期还没开售，中间本来就有空窗。端上这时候该说
 * 「本期已结束，下一期敬请期待」，而不是转圈或者报错。
 *
 * @param lotteryCode  彩票编码
 * @param lotteryName  玩法名
 * @param issueNo      当前在售期号。<b>没有在售期时为 null</b>
 * @param saleEndTime  本期停止发号的时刻
 * @param planDrawTime 计划开奖时间 —— 对外承诺的那个时刻，
 *                     和实际执行的 settle_time 不是一回事
 * @param myTicketCount 我在这一期已经有几张。没有在售期时为 0
 * @author alaric
 * @date 2026-09-18
 */
public record LotteryIssueView(
        String lotteryCode,
        String lotteryName,
        String issueNo,
        LocalDateTime saleEndTime,
        LocalDateTime planDrawTime,
        int myTicketCount) {
}
