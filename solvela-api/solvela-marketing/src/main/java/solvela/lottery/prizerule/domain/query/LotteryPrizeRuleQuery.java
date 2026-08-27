package solvela.lottery.prizerule.domain.query;

import solvela.base.domain.PageParam;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 彩票奖励规则分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryPrizeRuleQuery extends PageParam {

    /** 彩票编码 */
    private String lotteryCode;

    /**
     * 只看有体检告警的玩法。这是本页最主要的巡检入口 ——
     * 奖级配错的后果是「中了奖发不出去」或「某一级永远中不了」，都属于事后才发现、
     * 发现时钱已经赔出去的那类问题。
     */
    private Boolean onlyIssue;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

}
