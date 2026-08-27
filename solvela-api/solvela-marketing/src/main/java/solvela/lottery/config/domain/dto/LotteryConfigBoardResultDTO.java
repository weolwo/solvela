package solvela.lottery.config.domain.dto;

import java.util.List;
import lombok.Data;

/**
 * 彩票玩法一览的整体返回：顶部概览 + 当前页明细。
 * 概览与明细同一次请求算出，卡片数字与列表必然一致。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class LotteryConfigBoardResultDTO {

    /** 玩法总数（筛选后） */
    private Integer lotteryCount;

    /**
     * 当前真正能领号的玩法数：已上线 + 配了奖级 + 有正在售卖窗口内的待开奖期号。
     * 三者缺一，用户点领号都会被拒。
     */
    private Integer sellableCount;

    /** DANGER 级告警总数 */
    private Integer dangerCount;

    /** WARN 级告警总数 */
    private Integer warnCount;

    /** 累计已发号数 */
    private Long totalSold;

    /** 当前页的玩法明细 */
    private List<LotteryConfigBoardDTO> list;

    /** 玩法总数，供前端分页 */
    private Long total;
}
