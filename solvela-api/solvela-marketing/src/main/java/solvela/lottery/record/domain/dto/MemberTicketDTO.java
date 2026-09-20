package solvela.lottery.record.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的彩票」一行：号码本身 + 它所属那一期的开奖结果。
 *
 * <p>三张表 join 出来的形状（record / config / issue），只服务于 C 端那一页。
 * 刻意不复用 {@code LotteryRecordDTO} —— 那个是管理端用的，带着
 * 会员账号、签名、派发状态这些<b>不该下发给用户</b>的列，
 * 复用它迟早会有人顺手把某个字段透到 C 端去。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
public class MemberTicketDTO {

    private String lotteryCode;

    /** 玩法名。玩法被删了会是 null —— 端上按「彩票」兜底，票不能因此消失 */
    private String lotteryName;

    private String issueNo;

    private String ticketNumber;

    private LocalDateTime obtainTime;

    /** 0-未开奖 / 1-未中奖 / 2-已中奖 */
    private Integer winStatus;

    /** 1..N 为奖级，<b>99 是「未中奖/未开奖」的哨兵值</b>，不要直接下发 */
    private Integer prizeLevel;

    /** 开奖号码。未开奖是 null */
    private String winningNumber;

    /** 计划开奖时间 */
    private LocalDateTime planDrawTime;
}
