package solvela.lottery.config.domain.query;

import solvela.enums.LotteryConfigStatusEnum;
import solvela.base.domain.PageParam;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 彩票配置分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryConfigQuery extends PageParam {

    /** 活动编码 */
    private String activityCode;

    /** 彩票编码 */
    private String lotteryCode;

    /** 彩票名称 */
    private String lotteryName;

    /** 状态：0-下线, 1-上线 */
    private LotteryConfigStatusEnum status;

    /**
     * 只看有体检告警的玩法。「已上线却没配奖级」「上线了但没有可领号的期号」
     * 这类问题看裸字段一个都看不出来，而它们的后果是用户当场领不到号。
     */
    private Boolean onlyIssue;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

}
