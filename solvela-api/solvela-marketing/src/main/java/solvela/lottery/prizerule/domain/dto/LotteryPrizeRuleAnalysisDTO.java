package solvela.lottery.prizerule.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import solvela.base.stat.HealthIssue;

/**
 * 单条奖级规则的赔付模型与体检结果。
 *
 * <p>表里只有「匹配规则 + 匹配长度 + 奖品编码」三个字段，光看它们无法判断这条规则贵不贵、
 * 会不会永远中不了。这个 VO 把它们翻译成运营真正要看的三个数字：
 * <b>净中奖率 → 预计中奖注数 → 预计赔付成本</b>。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class LotteryPrizeRuleAnalysisDTO {

    /** 规则id */
    private Long id;

    /** 奖品奖级 */
    private Integer prizeLevel;

    /** 匹配规则原值: EXACT / TAIL / HEAD，可能是脏数据 */
    private String matchRule;

    /** 匹配规则中文说明，规则值非法时为原值 */
    private String matchRuleDesc;

    /** 匹配长度 */
    private Integer matchLength;

    /**
     * 不考虑更高奖级抢占时，一张号码满足本条规则的概率。
     * 它是「这条规则有多宽」的直观刻度，但不是实际中奖率。
     */
    private BigDecimal hitRate;

    /**
     * 扣掉被更高奖级抢先认领的部分之后，真正会落到本奖级的概率。
     * 这才是乘发行量得到中奖注数的那个数。为 0 表示该奖级<b>永远认领不到票</b>。
     * 为 null 表示奖级过多、容斥无法精确计算（见 PrizeRuleProbability）。
     */
    private BigDecimal netRate;

    /** 预计中奖注数 = 单期发行量 × 净中奖率 */
    private BigDecimal expectedWinCount;

    /** 奖品编码 */
    private String prizeCode;

    /** 奖品名称，奖品不存在时为 null */
    private String prizeName;

    /** 奖品类型 */
    private String prizeType;

    /** 奖品单价 */
    private BigDecimal prizeValue;

    /** 预计赔付成本 = 预计中奖注数 × 奖品单价 */
    private BigDecimal expectedCost;

    /** 本条规则的体检告警，空表示没问题 */
    private List<HealthIssue> issueList;
}
