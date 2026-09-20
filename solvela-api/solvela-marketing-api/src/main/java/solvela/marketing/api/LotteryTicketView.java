package solvela.marketing.api;

import java.time.LocalDateTime;

/**
 * 我的一张彩票号码。
 *
 * <h3>为什么把「哪一期」的信息也带上</h3>
 * 用户看一张票时的三个问题是：<b>什么号、开没开、中没中</b>。
 * 后两个的答案在期号那张表上（{@code winning_number} / {@code plan_draw_time}），
 * 不带下来的话端上就得按 lotteryCode + issueNo 再打 N 次接口 —— 那是跨进程的 N+1。
 *
 * @param lotteryCode   彩票编码
 * @param lotteryName   玩法名，给用户看的
 * @param issueNo       期号
 * @param ticketNumber  号码
 * @param obtainTime    领号时间
 * @param winStatus     0-未开奖 / 1-未中奖 / 2-已中奖
 * @param statusText    给用户看的那句话。<b>由服务端给</b> ——
 *                      端上做映射表就是第二份状态机
 * @param prizeLevel    中奖奖级（1 最大）。未中 / 未开奖时为 {@code null}，
 *                      <b>不是 99</b>：99 是库里的哨兵值，不该漏到端上
 * @param winningNumber 开奖号码。未开奖是 {@code null}
 * @param planDrawTime  计划开奖时间。未开奖时用它告诉用户「什么时候来看」；
 *                      已开奖后端上不用它
 * @author alaric
 * @date 2026-09-18
 */
public record LotteryTicketView(
        String lotteryCode,
        String lotteryName,
        String issueNo,
        String ticketNumber,
        LocalDateTime obtainTime,
        Integer winStatus,
        String statusText,
        Integer prizeLevel,
        String winningNumber,
        LocalDateTime planDrawTime) {
}
