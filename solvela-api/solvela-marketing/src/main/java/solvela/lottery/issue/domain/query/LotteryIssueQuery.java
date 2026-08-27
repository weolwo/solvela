package solvela.lottery.issue.domain.query;

import solvela.base.domain.PageParam;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 彩票期号分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryIssueQuery extends PageParam {

    /** 彩票编码 */
    private String lotteryCode;

    /** 期号 */
    private String issueNo;

    /** 状态: 0-待开奖, 1-核销中, 2-已开奖 */
    private Integer status;

    /**
     * 售卖态筛选，与 status 是两个维度：status 问「开奖走到哪一步」，它问「现在还能不能领号」。
     * 判定表达式与列表返回的 saleState 同源（mapper 里的 sale_state_expr），不会漂移。
     */
    private Integer saleState;

    /**
     * 只看逾期未开奖的期。这是本页最主要的巡检入口 ——
     * 到点没开奖是要赔付的，比「有多少期」重要得多。
     */
    private Boolean overdueOnly;

    /**
     * 只看今天计划开奖的期。
     *
     * 做成布尔量而不是让前端算出「今天」的起止时刻再传时间区间：
     * 「今天是哪天」也是一次时间判断，交给浏览器算就又多了一个时钟源（铁律 9/10）——
     * 客户端时区偏一格，筛出来的就是昨天或明天的活儿。
     * 这里由数据库 CURDATE() 判定，与概览卡片 todayDrawCount 共用同一个表达式，
     * 卡片上的数字与点进去的行数必然相等。
     */
    private Boolean todayDrawOnly;

    /** 计划开奖时间 开始 */
    private LocalDateTime planDrawTimeBegin;

    /** 计划开奖时间 结束 */
    private LocalDateTime planDrawTimeEnd;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

}
