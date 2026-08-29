package solvela.draw.poolconfig.domain.dto;

import solvela.enums.PrizePoolStatusEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 奖池配置一览行：表里的可编辑字段 + 「这个池现在能不能正常抽」的结论。
 *
 * <p>奖池配置页是 {@code reset_period} 与 {@code status} 的<b>唯一编辑入口</b>
 * （工作台只管 poolName，明确不覆盖这两个），所以它必须保留编辑能力。
 * 但光把表字段列出来看不出任何问题 —— 一个池能不能真的抽，取决于四件事的组合：
 * 活动上线了吗、奖池开着吗、坑位配了吗、概率闭环吗。
 * 这四个分散在三张表里，本 VO 把它们并到一行。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class PrizePoolBoardDTO {

    // ---------------- 表字段（可编辑，回填表单用） ----------------

    /** id */
    private Long id;

    /** 活动编码 */
    private String activityCode;

    /** 奖池编码 */
    private String poolCode;

    /** 奖池名称 */
    private String poolName;

    /** 限领重置周期: DAY/WEEK/MONTH/ACTIVITY */
    private String resetPeriod;

    /** 奖池开关: 0-关闭, 1-开启 */
    private PrizePoolStatusEnum status;

    /** 创建时间 */
    private LocalDateTime createTime;

    // ---------------- 派生（查询时算出，非表字段） ----------------

    /** 所属活动名称，活动不存在时为 null */
    private String activityName;

    /** 活动状态: 0-未上线, 1-已上线 */
    private Integer activityStatus;

    /** 坑位数量 */
    private Integer slotCount;

    /** 概率总和，必须等于 100 才能正常抽奖 */
    private BigDecimal probabilitySum;

    /** 概率是否闭环 */
    private Boolean probabilityClosed;

    /** 兜底奖项数量 */
    private Integer fallbackCount;

    /**
     * 池内配置了单人限领（{@code user_max_count != -1}）的奖项数。
     *
     * <p>这个数是判断 {@code reset_period} 有没有意义的唯一依据：
     * 一个都没有的话，重置周期配成什么都不影响任何行为 —— 因为压根没有计数要重置。
     */
    private Integer limitedItemCount;

    /**
     * 「现在能不能抽」的总结论：活动已上线 + 奖池开启 + 有坑位 + 概率闭环，四者同时成立。
     * 任一不成立，用户点抽奖要么进不来、要么直接报错。
     */
    private Boolean drawable;

    /** DANGER 级告警条数 */
    private Integer dangerCount;

    /** WARN 级告警条数 */
    private Integer warnCount;

    /** 体检告警明细 */
    private List<PoolConfigIssueDTO> issueList;
}
