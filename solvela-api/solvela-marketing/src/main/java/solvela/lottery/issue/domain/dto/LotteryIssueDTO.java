package solvela.lottery.issue.domain.dto;

import solvela.enums.IssueStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票期号列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class LotteryIssueDTO {


    private Long id;

    /** 彩票编码 */
    private String lotteryCode;

    /** 期号 */
    private String issueNo;

    /** 已售/已派发数量 */
    private Integer soldCount;

    /** 售卖开始时间 */
    private LocalDateTime saleStartTime;

    /** 售卖结束时间 */
    private LocalDateTime saleEndTime;

    /** 计划开奖时间：对外承诺的开奖时刻，可编辑 */
    private LocalDateTime planDrawTime;

    /** 实际开奖时间：由开奖流程写入，只读 */
    private LocalDateTime settleTime;

    /** 开奖号码 */
    private String winningNumber;

    /** 状态: 0-待开奖, 1-售卖中, 2-已开奖 */
    private IssueStatusEnum status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ---------------- 以下为查询时派生，非表字段 ----------------

    /** 玩法名称，join t_lottery_config 取 */
    private String lotteryName;

    /**
     * 所属活动编码，join t_lottery_config 取。
     * 列表不展示，只用于「去工作台」深链 —— 工作台是按 activityCode + lotteryCode 定位的，
     * 少了它就只能把运营丢到工作台首页让他自己再找一遍。
     */
    private String activityCode;

    /** 本期发行总数上限，join t_lottery_config 取，作为已发数的分母 */
    private Integer totalCount;

    /**
     * 「这一期现在还能不能领号」的结论。
     *
     * <p>取值与判定顺序<b>逐条对齐运行态</b> {@code TicketIssueService#obtain}，
     * 两边必须一起改：玩法上线 → 期号待开奖 → 未到开始 → 已过结束 → 售罄。
     * 顺序不能换：售罄判定在窗口之后，所以一个「还没开售」的期不会被显示成售罄。
     *
     * <p>它与 status 是两个维度：status 是生命周期（待开奖/核销中/已开奖），
     * 这个是售卖态。列表上必须两个都显示，否则「待开奖」这一个标签会同时盖住
     * 「还没开售」「正在售」「已停售待开」三种完全不同的处境。
     */
    private Integer saleState;

    /**
     * 已过计划开奖时间却还没开完奖。这是本页存在的理由 ——
     * 工作台一次只看一个玩法，看不到「哪几期到点了没人开」。
     */
    private Boolean overdue;

}
