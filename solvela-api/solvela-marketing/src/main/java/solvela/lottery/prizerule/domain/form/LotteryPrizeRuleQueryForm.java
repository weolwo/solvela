package solvela.lottery.prizerule.domain.form;

import solvela.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 彩票奖励配置 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryPrizeRuleQueryForm extends PageParam {

    @Schema(description = "彩票编码")
    private String lotteryCode;

    /**
     * 只看有体检告警的玩法。这是本页最主要的巡检入口 ——
     * 奖级配错的后果是「中了奖发不出去」或「某一级永远中不了」，都属于事后才发现、
     * 发现时钱已经赔出去的那类问题。
     */
    @Schema(description = "只看有体检告警的玩法")
    private Boolean onlyIssue;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
