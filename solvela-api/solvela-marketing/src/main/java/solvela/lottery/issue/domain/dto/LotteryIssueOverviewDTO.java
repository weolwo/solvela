package solvela.lottery.issue.domain.dto;

import lombok.Data;

/**
 * 期号巡检概览：列表页顶部四张卡片的数字。
 *
 * <p>这四个数是这个页面的立身之本。彩票工作台一次只盯一个玩法，
 * 看不到「所有玩法里哪几期到点没开奖」——那正是要赔付的那几期。
 *
 * <p>口径与列表的派生字段完全同源（mapper 里共用 sale_state_expr / overdue_expr），
 * 所以点卡片筛出来的行数必然等于卡片上的数字。两处各写一套 SQL 就会对不上。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class LotteryIssueOverviewDTO {

    /** 逾期未开奖：计划开奖时间已过，却还没开完奖 */
    private Integer overdueCount;

    /** 售卖中：现在真的能领到号的期 */
    private Integer onSaleCount;

    /** 已售罄：已发数达到发行上限 */
    private Integer soldOutCount;

    /** 今日计划开奖：计划开奖时间落在今天且尚未开完 */
    private Integer todayDrawCount;

}
