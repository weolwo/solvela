package solvela.lottery.config.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 彩票玩法一览行：表字段 + 号码空间占用 + 运营实况 + 体检结论。
 *
 * <h3>为什么号码空间要单独算出来</h3>
 * {@code number_length} 决定号码空间是 10^n，{@code total_count} 是单期发行上限，
 * 两者的比值才是「这个玩法还有多少扩容余地」。而更关键的是 ——
 * <b>一旦发出过号码，号码长度与发行上限就永久冻结</b>
 * （{@code resolveLockReason}：号码由游标加密得来，改参数会让已发号码无法验证、
 * 且新号码可能与历史重复）。所以占用率接近 100% 且已发过号，
 * 意味着这个玩法的发行量再也加不上去了，想扩容只能新建玩法 ——
 * 这件事必须在发号之前就让运营看见，事后再看已经晚了。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class LotteryConfigBoardDTO {

    // ---------------- 表字段 ----------------

    /** id */
    private Long id;

    /** 活动编码 */
    private String activityCode;

    /** 彩票编码 */
    private String lotteryCode;

    /** 彩票名称 */
    private String lotteryName;

    /** 号码长度 */
    private Integer numberLength;

    /** 单期发行总数上限 */
    private Integer totalCount;

    /** 状态: 0-未上线, 1-已上线 */
    private Integer status;

    // ---------------- 派生 ----------------

    /** 所属活动名称，活动不存在时为 null */
    private String activityName;

    /** 号码空间 = 10^号码长度 */
    private Long numberSpace;

    /**
     * 空间占用率 = 单期发行上限 / 号码空间。
     * 到 100% 意味着号码长度已被发行量吃满，再想扩容必须加长号码 ——
     * 而发过号之后号码长度永久冻结。
     */
    private BigDecimal spaceUsage;

    /** 期号总数 */
    private Integer issueCount;

    /** 待开奖期号数 */
    private Integer waitIssueCount;

    /** 已开奖期号数 */
    private Integer openedIssueCount;

    /** 累计已发号数（各期 sold_count 之和） */
    private Long soldTotal;

    /** 奖级规则条数 */
    private Integer ruleCount;

    /** 累计参与人数（去重会员） */
    private Long memberCount;

    /** 累计中奖注数 */
    private Long winCount;

    /**
     * 发号引擎参数是否已冻结：只要发出过号码就为 true。
     * 冻结后号码长度与发行上限都不能再改。
     */
    private Boolean paramsFrozen;

    /** DANGER 级告警条数 */
    private Integer dangerCount;

    /** WARN 级告警条数 */
    private Integer warnCount;

    /** 体检告警明细 */
    private List<LotteryConfigIssueDTO> issueList;
}
