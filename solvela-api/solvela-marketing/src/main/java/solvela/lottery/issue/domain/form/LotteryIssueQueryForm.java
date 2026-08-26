package solvela.lottery.issue.domain.form;

import solvela.base.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 期号配置 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryIssueQueryForm extends PageParam {

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "期号")
    private String issueNo;

    @Schema(description = "状态: 0-待开奖, 1-核销中, 2-已开奖")
    private Integer status;

    /**
     * 售卖态筛选，与 status 是两个维度：status 问「开奖走到哪一步」，它问「现在还能不能领号」。
     * 判定表达式与列表返回的 saleState 同源（mapper 里的 sale_state_expr），不会漂移。
     */
    @Schema(description = "售卖态: 0-未开始, 1-售卖中, 2-已结束, 3-已停止发号, 4-玩法不可售, 5-已售罄")
    private Integer saleState;

    /**
     * 只看逾期未开奖的期。这是本页最主要的巡检入口 ——
     * 到点没开奖是要赔付的，比「有多少期」重要得多。
     */
    @Schema(description = "只看逾期未开奖")
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
    @Schema(description = "只看今日计划开奖（由数据库 CURDATE() 判定）")
    private Boolean todayDrawOnly;

    @Schema(description = "计划开奖时间 开始")
    private LocalDateTime planDrawTimeBegin;

    @Schema(description = "计划开奖时间 结束")
    private LocalDateTime planDrawTimeEnd;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
