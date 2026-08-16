package sa.lottery.prizerule.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 一个玩法的完整奖励结构：奖级明细 + 整体赔付模型 + 体检结论。
 *
 * <p>页面按玩法分组而不是平铺规则行，是因为<b>奖级只有放在一起才有意义</b> ——
 * 净中奖率要扣更高奖级的抢占、总赔付成本要逐级相加，单看一行规则什么都判断不了。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class LotteryPrizeAnalysisVO {

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "玩法名称，玩法配置缺失时为 null")
    private String lotteryName;

    @Schema(description = "所属活动编码，供跳转工作台深链使用")
    private String activityCode;

    @Schema(description = "号码长度，概率模型的底数指数")
    private Integer numberLength;

    @Schema(description = "单期发行总数上限，预计中奖注数的乘数")
    private Integer totalCount;

    @Schema(description = "玩法状态: 0-未上线, 1-已上线。已上线且有告警的最该先处理")
    private Integer lotteryStatus;

    /**
     * 各奖级净中奖率之和 —— 一张号码中任意一个奖的概率。
     * 因为净中奖率之间互斥（一张票只中一级），直接相加即可，不需要再做容斥。
     */
    @Schema(description = "总中奖率：一张号码中任意奖级的概率")
    private BigDecimal totalNetRate;

    @Schema(description = "预计单期中奖总注数")
    private BigDecimal totalExpectedWinCount;

    /**
     * 单期跑满（发行量全部售出）时的预计赔付总额。
     * 这是配奖级时最该先看的数字，而系统此前没有任何一处显示它。
     */
    @Schema(description = "预计单期赔付总成本")
    private BigDecimal totalExpectedCost;

    @Schema(description = "本玩法 DANGER 级告警条数")
    private Integer dangerCount;

    @Schema(description = "本玩法 WARN 级告警条数")
    private Integer warnCount;

    @Schema(description = "玩法维度的体检告警（如未配任何奖级）")
    private List<PrizeRuleIssueVO> issueList;

    @Schema(description = "奖级明细，按奖级升序 —— 顺序即开奖认领顺序")
    private List<LotteryPrizeRuleAnalysisVO> ruleList;
}
