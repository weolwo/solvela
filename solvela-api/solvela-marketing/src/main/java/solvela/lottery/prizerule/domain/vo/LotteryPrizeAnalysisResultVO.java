package solvela.lottery.prizerule.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 奖励结构分析的整体返回：顶部概览 + 当前页的玩法明细。
 *
 * <p>概览与明细<b>在同一次请求里算出来</b>，不拆成两个接口：
 * 两个接口意味着两次独立查询，中间数据一变，卡片上的数字就会和下面列出来的对不上。
 * 期号巡检页那四张卡也是同一个理由（那边靠共用 SQL 表达式，这边靠共用一次计算）。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class LotteryPrizeAnalysisResultVO {

    @Schema(description = "配了奖级的玩法数（筛选后）")
    private Integer lotteryCount;

    @Schema(description = "奖级规则总条数（筛选后）")
    private Integer ruleCount;

    @Schema(description = "DANGER 级告警总数：会导致开奖或派奖出错")
    private Integer dangerCount;

    @Schema(description = "WARN 级告警总数：配置可疑但能跑")
    private Integer warnCount;

    @Schema(description = "所有玩法各跑满一期的预计赔付总成本")
    private BigDecimal totalExpectedCost;

    @Schema(description = "当前页的玩法明细")
    private List<LotteryPrizeAnalysisVO> list;

    @Schema(description = "玩法总数，供前端分页")
    private Long total;
}
